import urllib.request
import json

req1 = urllib.request.Request(
    "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=AIzaSyDIuvK8qU57Wfi33U_dMa05AqeVgmwKpVE",
    data=b"{\"returnSecureToken\":true}",
    headers={"Content-Type": "application/json"}
)
try:
    res1 = urllib.request.urlopen(req1)
    token = json.loads(res1.read())["idToken"]
    print("Got auth token")
    
    req2 = urllib.request.Request(
        "https://firestore.googleapis.com/v1/projects/guardianai-kj/databases/(default)/documents/devices?documentId=testDevice123",
        data=b"{\"fields\":{\"testMessage\":{\"stringValue\":\"Hello from Agent\"}}}",
        headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"}
    )
    res2 = urllib.request.urlopen(req2)
    print("SUCCESS FIRESTORE: " + res2.read().decode())
except urllib.error.HTTPError as e:
    print("ERROR: " + str(e.code) + " " + e.read().decode())
