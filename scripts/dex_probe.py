import struct, pathlib, hashlib, json, sys

class Dex:
    def __init__(self, path):
        self.path = path
        self.b = path.read_bytes()
        assert self.b[:4] == b"dex\n"
        self.strings = []
        for i in range(self.u4(0x38)):
            off = self.u4(self.u4(0x3c) + 4*i)
            _, off = self.uleb(off)
            end = self.b.index(b"\0", off)
            self.strings.append(self.b[off:end].decode("utf-8", "replace"))
        self.types = [self.strings[self.u4(self.u4(0x44)+4*i)] for i in range(self.u4(0x40))]
        self.protos = []
        for i in range(self.u4(0x48)):
            base = self.u4(0x4c)+12*i
            ret, off = self.u4(base+4), self.u4(base+8)
            pars = [] if off == 0 else [self.types[self.u2(off+4+2*j)] for j in range(self.u4(off))]
            self.protos.append((self.types[ret], pars))
        self.methods = []
        for i in range(self.u4(0x58)):
            base = self.u4(0x5c)+8*i
            cls, proto, name = self.u2(base), self.u2(base+2), self.u4(base+4)
            ret, pars = self.protos[proto]
            self.methods.append(dict(owner=self.types[cls], name=self.strings[name], ret=ret, params=pars))
        self.code = {}
        for i in range(self.u4(0x60)):
            off = self.u4(self.u4(0x64)+32*i+24)
            if off == 0: continue
            counts=[]
            for _ in range(4):
                n, off = self.uleb(off); counts.append(n)
            for _ in range(counts[0]+counts[1]):
                _, off=self.uleb(off); _, off=self.uleb(off)
            # DEX deltas restart independently for direct and virtual method arrays.
            for count in counts[2:]:
                index=0
                for _ in range(count):
                    delta, off=self.uleb(off); index+=delta
                    _, off=self.uleb(off); code, off=self.uleb(off)
                    if code: self.code[index]=code
        self.refs={i:self.scan(off) for i,off in self.code.items()}
    def u2(self,o): return struct.unpack_from("<H",self.b,o)[0]
    def u4(self,o): return struct.unpack_from("<I",self.b,o)[0]
    def uleb(self,o):
        n=0;shift=0
        while True:
            b=self.b[o];o+=1;n|=(b&127)<<shift
            if b<128:return n,o
            shift+=7
            assert shift<=35
    def scan(self,off,fields_only=False):
        n=self.u4(off+12); start=off+16; pos=0; strings=set(); calls=set(); fields=set()
        while pos<n:
            raw=self.u2(start+2*pos); op=raw&255
            width=1
            if op==0 and raw>>8:
                kind=raw>>8; size=self.u2(start+2*(pos+1))
                if kind==1:width=4+2*size
                elif kind==2:width=2+4*size
                elif kind==3:width=4+(size*self.u4(start+2*(pos+2))+1)//2
                else:raise ValueError("unknown payload")
            elif op in (0x18,):width=5
            elif op in (0xfa,0xfb):width=4
            elif op in (0x03,0x06,0x09,0x14,0x17,0x1b,0x24,0x25,0x26,0x2a,0x2b,0x2c,0xfc,0xfd) or 0x6e<=op<=0x72 or 0x74<=op<=0x78:width=3
            elif op in (0x02,0x05,0x08,0x13,0x15,0x16,0x19,0x1a,0x1c,0x1f,0x20,0x22,0x23,0x29,0xfe,0xff) or 0x2d<=op<=0x3d or 0x44<=op<=0x6d or 0x90<=op<=0xaf or 0xd0<=op<=0xe2:width=2
            assert pos+width<=n,(self.path,off,pos,op,width,n)
            if op==0x1a:strings.add(self.strings[self.u2(start+2*(pos+1))])
            elif op==0x1b:strings.add(self.strings[self.u4(start+2*(pos+1))])
            if 0x6e<=op<=0x72 or 0x74<=op<=0x78:calls.add(self.u2(start+2*(pos+1)))
            if 0x52<=op<=0x6d:fields.add(self.u2(start+2*(pos+1)))
            pos+=width
        assert pos==n
        return fields if fields_only else (strings,calls)
    def field(self,index):
        b=self.u4(0x54)+8*index
        return dict(owner=self.types[self.u2(b)],type=self.types[self.u2(b+2)],name=self.strings[self.u4(b+4)])
    def describe(self,i):
        m=self.methods[i]
        return m["owner"]+"->"+m["name"]+"("+"".join(m["params"])+")"+m["ret"]
    def shape(self,i):
        m=self.methods[i]
        return len(m["params"])==5 and m["params"][:3]==["J","Ljava/lang/String;","J"] and m["ret"]=="Ljava/util/Map;"
    def report(self):
        gates=[i for i,(s,c) in self.refs.items() if {"confirmationDenied","confirmationUnavailable"}<=s]
        candidate=set()
        for g in gates:
            if self.shape(g):candidate.add(g)
            for i,(s,cs) in self.refs.items():
                if g in cs and self.shape(i):candidate.add(i)
        final=[i for i in candidate if {"start","poll","jobId","command"}<=self.refs[i][0]]
        links=[]
        for i in final:
            for g in gates:
                if g==i or g in self.refs[i][1]:
                    links.append(dict(execute=self.describe(i), gate=self.describe(g), gate_params=self.methods[g]["params"]))
        mapping_evidence=[]
        constants={'ALLOW_ONCE','ALLOW_APP_FOR_TASK','ALLOW_ALL_FOR_TASK','ALLOW_VISUAL_FOR_TASK','DENY','STOP','UNAVAILABLE'}
        for i in final:
            for fidx in self.scan(self.code[i], True):
                f=self.field(fidx)
                if f['type']!='[I':continue
                for j,off in self.code.items():
                    if self.methods[j]['owner']!=f['owner'] or self.methods[j]['name']!='<clinit>':continue
                    for enumfieldidx in self.scan(off, True):
                        ef=self.field(enumfieldidx)
                        if ef['type']!=ef['owner']:continue
                        for k,eoff in self.code.items():
                            if self.methods[k]['owner']==ef['owner'] and self.methods[k]['name']=='<clinit>' and constants<=self.refs[k][0]:
                                item=dict(execute=self.describe(i),mapping=f,enum_type=ef['owner'],enum_constants=sorted(constants))
                                if item not in mapping_evidence:mapping_evidence.append(item)
        return dict(dex=str(self.path),sha256=hashlib.sha256(self.b).hexdigest(),defined_methods=len(self.code),gates=[self.describe(g) for g in gates],shape_candidates=len(candidate),protocol_candidates=[self.describe(i) for i in final],links=links,enum_mapping_evidence=mapping_evidence)

if __name__=="__main__":
    out=[Dex(pathlib.Path(p)).report() for p in sys.argv[1:]]
    print(json.dumps(out,ensure_ascii=False,indent=2))
