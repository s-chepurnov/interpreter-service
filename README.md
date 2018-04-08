#### Running
```bash
sbt run
```

#### REST API

GET        /                                
POST       /execute                         
POST       /validate                        
GET        /getDic                          

#### Examples
#####Verify random script with custom Environment

curl --header "Content-type: application/json" --request POST --data '{"scriptText": "{HEIGHT >= timeout && backerPubKey}", "env" : [{"key": "backerPubKey", "value":"Alice", "type" : "pubKey"}, {"key": "projectPubKey", "value":"Bob", "type" : "pubKey"}, {"key": "timeout", "value":"100", "type" : "Int"}, {"key": "minToRaise", "value":"1000", "type" : "Int"}]}' http://localhost:9000/validate

#####Execute RingSignature with PreSet

curl --header "Content-type: application/json" --request POST --data '{"scriptText": "pubkeyA || pubkeyB", "env" : [{"key": "pubkeyA", "value":"Alice", "type" : "pubKey"}, {"key": "pubkeyB", "value":"Bob", "type" : "pubKey"}], "contextPreset" : "RING"}' http://localhost:9000/execute

#####Execute Crowdfunding with PreSet

curl --header "Content-type: application/json" --request POST --data '{"scriptText": "anyOf(Array(HEIGHT >= timeout && backerPubKey,allOf(Array(HEIGHT < timeout,projectPubKey,OUTPUTS.exists(fun (out: Box) = {out.value >= minToRaise && out.propositionBytes == projectPubKey.propBytes})))))", "env" : [{"key": "backerPubKey", "value":"Alice", "type" : "pubKey"}, {"key": "projectPubKey", "value":"Bob", "type" : "pubKey"}, {"key": "timeout", "value":"100", "type" : "Int"}, {"key": "minToRaise", "value":"1000", "type" : "Int"}], "contextPreset" : "CROWD"}' http://localhost:9000/execute