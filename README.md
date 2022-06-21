![](https://github.com/fmsh-seclab/TesMla/blob/master/images/banner.png)
TesMla
========

Introduction
-------
This app implements a **man-in-the-middle-attack** to Tesla Model 3 and its phone keys.

Background
----------
Tesla enables car owners to use their smart phones to automatic unlock and start their cars. Once paired, the vehicle automatically authenticates the Phone key when the key is approaching. TesMla exploits vulnerabilities of authentication process. It works as an intermediate, storing and forwarding messages between the key and the vehicle. Finally TesMla tricks the Model 3 to believe that there is a legitimate key in range.   
The following parts review the authentication protocols by reverse engineering and sniffing. The vulnerabilities and proof of concept are shown as follows either.

### Test environments
The table below shows the model of devices used in sniffing and attack. 
|Devices|Model|Version|
|-------|--------|-----|
|Owner device|Motorola Edge S|Android 11|
|Attack device| Google Pixel 5A| Customized Android 11|
|Vehicle|Tesla Model 3|V11
|App|Tesla app|V4.2.3|

### Authentication protocols
If a phone key has been paired, it has the public key and BLE MAC address of Model 3. Similarly, Model 3 has the public key of phone key. When a paired phone is approaching the vehicle, Model 3 authenticates it through BLE channel. The vehicle unlocks the door if authentication success. This process is analyzed using BLE HCI log from the owner device and reverse-engineering of Tesla App. We provide a comprehensive protocol of authentication as follow.

![](https://github.com/fmsh-seclab/TesMla/blob/master/images/a.jpg)

When a phone key is approaching, it sends request of connection to a specific MAC address. If the vehicle is in the BLE range, the reconnection establishes. Model 3 sends authentication indications to phone. This indication initiates the authentication process (Step 1). The Phone may optionally send information request to get public key or other status of the vehicle. The vehicle sends corresponding data back. (Step 4 - 5).  
The phone generates shared secret `S` using local private key `p` and public key of vehicle `V` by `ECDH`. Then it encrypts data `a` with `S` as key and counter `count` as IV in AES-GCM mode. Data `a` in two bytes is the serialized result of an unsigned message through Protobuf. The operation to get data `a` is shown as follows.
 ```java
 UnsignedMessage{AuthenticationResponse:AuthenticationResponse {authenticationLevel: AUTHENTICATION_LEVEL_NONE }}
 ```  
  
Phone delivers encryption results to Model 3. The result as first attestation consists of ciphertext(2bytes), tag(16bytes) and counter to Model 3. (Step 6 - 8). Model 3 derives secret S according to local private key `v` and public key of phone key using `ECDH`. Once receiving the first attestation, Model 3 replies the counter first. Then it verifies data based on `S` and responds token `G` (20bytes) when verification successes. (Step 9 - 12).   
Phone key increments counter by one. Once receiving token, phone key encrypts data `b` with `S` as key, new counter as IV and token `G` as additional authentication data in AES-GCM mode. Data `b` is the serialized result (4bytes) defined as below.  
```java
UnsignedMessage{AuthenticationResponse: AuthenticationResponse {authenticationLevel: AUTHENTICATION_LEVEL_DRIVE }}
```  
  
Afterwards phone key sends results as second attestation to vehicle (Step 13 - 14). Model 3 verifies the second attestation. If it passes, Model 3 unlocks the door and authentication process finishes.


### Vulnerabilities

- The reconnection between vehicle and Phone Key depends only on BLE MAC address of vehicle. A Tesla Model 3 broadcasts using its public MAC address which is always static. The Phone Key will not distinguish the vehicle from other malicious devices with same BLE MAC address, because reconnection does not check other information.  
- BLE communications are all in plaintext. It offers adversaries opportunities to sniff. 
- The value of 20 bytes token G stays fixed for hours. The connection status will not lead to token update. The token may remain the same no matter Phone key and Model 3 have connected and disconnected multiple times. 

BLE communication does not employ any security mechanisms. Instead, Tesla decides to leave security mechanisms to the upper layer. The up-level cryptography ensures the security by verifying two attestations in sequence. However, this kind of authentication method is suspectable to relay attack.  
By exploiting these weaknesses, we introduce a **man in the middle** attack. Attacker as an intermediation between Model 3 and Phone Key keeps recording and forwarding messages. Relay attack ideally needs two devices connecting to Model 3 and Phone Key separately. Between these two attack devices, there exists another low-latency and long-distance communication, like SMS/network etc.. Phone key treats attacker as paired vehicle and Model 3 seems attacker as a valid phone key. As a result, Model 3 is fooled to believe that it has connected to a registered key. It will unlock the door and start.


### Proof of concept
To prove feasibility of attack, we consider to use just one attack device. It requires adversary to connect to Phone Key and vehicle alternatively. 

**TesMla** is a proof of concept. This app implements functions as the Phone Key using `BluetoothGatt` and the Tesla Model 3 using `BluetoothGattServer`. The attack device installed TesMla can complete a man in the middle attack shown in Figure below. TesMla records messages necessarily and forwards to peer device.

![](https://github.com/fmsh-seclab/TesMla/blob/master/images/m.jpg)  

First of all, attacker needs to scan the broadcast packages from the vehicle to be attacked. Parsing the captured broadcast, attacker can specify BLE address of attack devices same as the vehicle. Attacker can optionally connect to the vehicle for some information. (Step 1 - 4).  
The attacker periodically sends broadcast which is same as the one captured before. If the attacker is close to the owner, the reconnection between the phone key and the attack device establishes automatically since the attack device has specific BLE MAC address. This connection let attacker get the first attestation package. (Step 6 - 13).  
Back to the vehicle, attacker sends connection request and reconnects to Model 3. And the attack device sends the first attestation captured before to get the response of token. (Step 15 - 22).  
Similarly reconnecting to the phone key, the attacker responses token once receiving the new attestation. This time attacker can get pair of attestations and record locally.  (Step 26 - 35).  
Finally, the attack device sends these two new attestations to the vehicle. If the token responded is same as before, the authentication passes and the vehicle unlocks and can be started. (Step 40 - 44).




Requirements
-----
All for Android. We test this app on a customized Google Pixel 5A Android Device.  
- You need use customized android device to broadcaset with static BLE address. Following the instructions on Google android source websites, users need download the android source code, and modify the definition `BLE_LOCAL_PRIVACY_ENABLED` from True to False, disable random Bluetooth Devices Address (BD_ADDR) features during Bluetooth advertising.   
- Based on the reverse engineering of Pixel 5A’s vendor firmware, we found the best way is to set the `ro.vendor.bt.boot.macaddr` property through an ADB shell command. Using this command to specify the BLE address.

User manual
------  
### Interface
Bootom navigation bar displays three destinations: Fake as Tesla, Scan and Fake as app.  
**Fake as Tesla:**
- Broadcast: Broadcast with specific BLE name and additional data.
- BLE Server: Provide a service with three characteristics. Records and response automatically.

	| Service | UUID | Property| Description|
	| ------- | ------- |---------|----|
	| characteristic 1 | 00000212-b2d1-43f0-9b88-960cebf8b91e|  Write |Receive data from peer device|
	| characteristic 2 | 00000213-b2d1-43f0-9b88-960cebf8b91e|  Indicate |Send data to peer device|
	| characteristic 3 | 00000214-b2d1-43f0-9b88-960cebf8b91e|  Read |Version Information|

**Scan:**
- Scan surroundings: Search BLE devices in range and show their BLE names and addresses in list.  
- Change MAC address: Change local MAC address to specific one. (This function may not work if you use an offical Android device which version higher than Android 5.)

**Fake as app:**
- Connect: Connect to device with specific MAC address
- Change MTU Size: Change MTU size to 256 bytes
- BLE Client: Send write request to server. Parse indication from server. Record and response automatically.  

![](https://github.com/fmsh-seclab/TesMla/blob/master/images/s1.JPG)  

### Operation Procedure
- 1. Touch `Scan` item. Click `start scan` button and then all BLE devices in range will be shown in a list. Choose the Tesla BLE name "SxxxxxxxxxxxxxxxxcC". Click `Change MAC` button. 
- 2. Touch `Fake as App` item. Click `connect` button and `Change size of MTU`. Click `get public key of Tesla`. Click `disconnect` button. (optional)
- 3. Touch 'Fake as Tesla' item. Click `fake broadcast` button.
- 4. Touch `Fake as App` item. Click `connect` button and `Change size of MTU`. Click  `Send data` button. 
- 5. Touch `Fake as Tesla` item. Click `fake broadcast` button. 
- 6. Touch `Fake as App` item. Click `connect` button and `Change size of MTU`. Click  `Send data` button. The Tesla Model 3 unlocks the door and can be driven.
