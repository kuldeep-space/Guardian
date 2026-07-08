import urllib.request
import json

# Get Auth Token
req1 = urllib.request.Request(
    "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=AIzaSyDIuvK8qU57Wfi33U_dMa05AqeVgmwKpVE",
    data=b"{\"returnSecureToken\":true}",
    headers={"Content-Type": "application/json"}
)
res1 = urllib.request.urlopen(req1)
token = json.loads(res1.read())["idToken"]

# Read Devices Collection
req2 = urllib.request.Request(
    "https://firestore.googleapis.com/v1/projects/guardianai-kj/databases/(default)/documents/devices",
    headers={"Authorization": "Bearer " + token}
)
try:
    res2 = urllib.request.urlopen(req2)
    data = json.loads(res2.read().decode())
    documents = data.get("documents", [])
    
    print(f"Total Devices Found: {len(documents)}")
    for doc in documents:
        print(f"- Device Name: {doc['name'].split('/')[-1]}")
        # Try to read apps subcollection for this device
        try:
            req3 = urllib.request.Request(
                f"https://firestore.googleapis.com/v1/projects/guardianai-kj/databases/(default)/documents/devices/{doc['name'].split('/')[-1]}/apps",
                headers={"Authorization": "Bearer " + token}
            )
            res3 = urllib.request.urlopen(req3)
            apps_data = json.loads(res3.read().decode())
            apps = apps_data.get("documents", [])
            print(f"  -> Apps synced: {len(apps)}")
            if len(apps) > 0:
                print(f"  -> First app: {apps[0]['name'].split('/')[-1]}")
        except Exception as e:
            print(f"  -> No apps found or error: {e}")
            
except urllib.error.HTTPError as e:
    print("ERROR: " + str(e.code) + " " + e.read().decode())
