#### Running
```bash
sbt run
```

#### REST API

GET        /

GET        /test

GET        /getContext

GET        /compileScript/:script

success example http://localhost:9000/compileScript/{anyOf(Array(HEIGHT,height1+deadlineA&&pubkeyA,pubkeyC&&blake2b256(taggedByteArray(1))==hx))}
failure example http://localhost:9000/compileScript/{anyOf(Array(HEIGHT404,height1+deadlineA&&pubkeyA,pubkeyC&&blake2b256(taggedByteArray(1))==hx))}
