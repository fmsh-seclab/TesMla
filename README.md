![](https://github.com/fmsh-seclab/TesMla/blob/master/images/banner.png)
TesMla
========
The app takes advantage of a collection of security issues discovered in the Model 3's keyless entry system that together add up to a method to fully unlock, start, and steal a vehicle.  
**NOTE:** All sources here are for **research use only**. 

Introduction
-------
Turning your phone into a key fob has become a trend. The phone as a key can be authenticated by the vehicle automatically provided that it is within Bluetooth Low Energy (BLE) range. Tesla provides this feature for better driver experiences. To use the phone hands-free, you must pair it first. As you approach, your phone’s Bluetooth signal is detected and doors unlock when you press a door handle.  

Security researchers， at Shanghai Fudan Microelectronics Group Company Ltd.， revealed a collection of security vulnerabilities they found in Tesla Model 3 cars and their phone keys. They discovered that those combined vulnerabilities could be exploited by any car thief who has installed this TesMla app on their phone. Within one minute, a hacker can fully unlock, start and steal a Model 3. Taking an android phone as an attack device, the whole attack process is out of the owner’s awareness and simple to be conducted. All these vulnerabilities and attack details have been reported to Tesla and the company didn’t respond. However, This attack brings into question the use of Bluetooth communication in security-critical applications since the attack is low-cost and concealed. 

This app open source here implements a **man-in-the-middle-attack** on Tesla Model 3 and its phone keys. Attackers can utilize this app to break Model 3 within one minute.  

Background
----------
Tesla enables car owners to use their smartphones to automatically unlock and activate their cars. Once paired, the vehicle automatically authenticates the Phone key when the key is approaching. TesMla exploits vulnerabilities of the authentication process and completes a man-in-the-middle attack to break Model 3. It works as an intermediate, storing and forwarding messages between the key and the vehicle. The following parts review the authentication protocols by reverse engineering and sniffing. The vulnerabilities and proof of concept are shown as follows either.
### Test environments
The table below shows the model of devices used in sniffing and attacking. 
|Devices|Model|Version|
|-------|--------|-----|
|Owner device|Motorola Edge S|Android 11|
|Attack device| Google Pixel 5A| Customized Android 11|
|Vehicle|Tesla Model 3|V11
|App|Tesla app|V4.2.3|

### Authentication protocols
If a phone key has been paired, it has the public key and BLE MAC address of Model 3. Similarly, Model 3 has the public key of the phone key. When a paired phone is approaching the vehicle, Model 3 authenticates it through the BLE channel. The vehicle unlocks the door if authentication success. This process is analyzed using the BLE HCI log from the owner device and reverse-engineering of the Tesla App. We provide a comprehensive protocol of authentication as follows.

![](https://github.com/fmsh-seclab/TesMla/blob/master/images/a.jpg)

When a phone key is approaching, it sends a request for connection to a specific MAC address. If the vehicle is in the BLE range, the reconnection establishes. Model 3 sends authentication indications to the phone. This indication initiates the authentication process (Step 1). The Phone may optionally send information requests to get the public key or status of the vehicle. The vehicle sends corresponding data back. (Steps 4 - 5).  
The phone generates shared secret `S` using local private key `p` and public key of vehicle `V` by `ECDH`. Then it encrypts data `a` with `S` as key and the counter `count` as IV in AES-GCM mode. Data `a` in two bytes is the serialized result of an unsigned message through Protobuf. The operation to get data `a` is shown as follows.
 ```java
 UnsignedMessage{AuthenticationResponse:AuthenticationResponse {authenticationLevel: AUTHENTICATION_LEVEL_NONE }}
 ```  
  
The phone delivers encryption results to Model 3. The result as the first attestation consists of ciphertext(2bytes), tag(16bytes), and counter to Model 3. (Steps 6 - 8). Model 3 derives secret S according to local private key `v` and public key of phone key using `ECDH`. Once receiving the first attestation, Model 3 replies to the counter first. Then it verifies data based on `S` and responds token `G` (20bytes) when verification successes. (Steps 9 - 12).   
Phone key increments counter by one. Once receiving the token, the phone key encrypts data `b` with `S` as key, new counter as IV, and token `G` as additional authentication data in AES-GCM mode. Data `b` is the serialized result (4bytes) defined as below.  
```java
UnsignedMessage{AuthenticationResponse: AuthenticationResponse {authenticationLevel: AUTHENTICATION_LEVEL_DRIVE }}
```  
  
Afterward phone key sends results as a second attestation to a vehicle (Steps 13 - 14). Model 3 verifies the second attestation. If it passes, Model 3 unlocks the door and the authentication process finishes.


### Vulnerabilities

- The reconnection between vehicle and Phone Key depends only on the BLE MAC address of the vehicle. A Tesla Model 3 broadcasts using its public MAC address which is always static. The Phone Key will not distinguish the vehicle from other malicious devices with the same BLE MAC address, because reconnection does not check other information.  
- BLE communications are all in plaintext. It offers adversaries opportunities to sniff. 
- The value of 20 bytes token G stays fixed for hours. The connection status will not lead to token updates. The token may remain the same no matter Phone key and Model 3 have connected and disconnected multiple times. 

BLE communication does not employ any security mechanisms. Instead, Tesla decides to leave security mechanisms to the upper layer. The up-level cryptography ensures security by verifying two attestations in sequence. However, this kind of authentication method is suspectable to relay attacks.  
By exploiting these weaknesses, we introduce a **man in the middle** attack. Attacker as intermediation between Model 3 and Phone Key keeps recording and forwarding messages. Relay attack ideally needs two devices connecting to Model 3 and Phone Key separately. Between these two attack devices, there exists other low-latency and long-distance communication, like SMS/network, etc. The phone key treats the attacker as paired vehicle and Model 3 seems to the attacker as a valid phone key. As a result, Model 3 is fooled to believe that it has connected to a registered key. It will unlock the door and start.


### Proof of concept
To prove the feasibility of the attack, we consider using just one attack device. It requires the adversary to connect to the Phone Key and vehicle alternatively. 

**TesMla** is a proof of concept. This app implements functions such as the Phone Key using `BluetoothGatt` and the Tesla Model 3 using `BluetoothGattServer`. The attack device installed TesMla can complete a man-in-the-middle attack shown in the Figure below. TesMla records messages necessarily and forwards them to peer devices.

![](https://github.com/fmsh-seclab/TesMla/blob/master/images/m.jpg)  

First of all, the attacker needs to scan the broadcast packages from the vehicle to be attacked. Parsing the captured broadcast, an attacker can specify the BLE address of attack devices the same as the vehicle. An attacker can optionally connect to the vehicle for some information. (Steps 1 - 4).  
The attacker periodically sends a broadcast that is the same as the one captured before. If the attacker is close to the owner, the reconnection between the phone key and the attack device establishes automatically since the attack device has a specific BLE MAC address. This connection let an attacker get the first attestation package. (Steps 6 - 13).  
Back to the vehicle, the attacker sends a connection request and reconnects to Model 3. And the attack device sends the first attestation captured before getting the response of the token. (Steps 15 - 22).  
Similarly reconnecting to the phone key, the attacker responds token once receiving the new attestation. This time attacker can get pair of attestations and record them locally.  (Steps 26 - 35).  
Finally, the attack device sends these two new attestations to the vehicle. If the token response is the same as before, the authentication passes and the vehicle unlocks and can be started. (Steps 40 - 44).




Requirements
-----
All for Android. We test this app on a customized Google Pixel 5A Android Device.  
- You need to use a customized android device to broadcast with a static BLE address. Following the instructions on Google android source websites, users need to download the android source code and modify the definition `BLE_LOCAL_PRIVACY_ENABLED` from True to False, disable random Bluetooth Devices Address (BD_ADDR) features during Bluetooth advertising.   
- Based on the reverse engineering of Pixel 5A’s vendor firmware, we found the best way is to set the `ro.vendor.bt.boot.macaddr` property through an ADB shell command. Use this command to specify the BLE address.

User manual
------  
### Interface
The bottom navigation bar displays three destinations: Fake as Tesla, Scan, and Fake as app.  
**Fake as Tesla:**
- Broadcast: Broadcast with specific BLE name and additional data.
- BLE Server: Provide a service with three characteristics. Records and responds automatically.

	| Service | UUID | Property| Description|
	| ------- | ------- |---------|----|
	| characteristic 1 | 00000212-b2d1-43f0-9b88-960cebf8b91e|  Write |Receive data from peer device|
	| characteristic 2 | 00000213-b2d1-43f0-9b88-960cebf8b91e|  Indicate |Send data to peer device|
	| characteristic 3 | 00000214-b2d1-43f0-9b88-960cebf8b91e|  Read |Version Information|

**Scan:**
- Scan surroundings: Search BLE devices in range and show their BLE names and addresses in the list.  
- Change MAC address: Change the local MAC address to a specific one. (This function may not work if you use an official Android device whose version higher than Android 5.)

**Fake as app:**
- Connect: Connect to a device with a specific MAC address
- Change MTU Size: Change MTU size to 256 bytes
- BLE Client: Send a request for writing data to the server. Parse indication from the server. Record and respond automatically.  

![](https://github.com/fmsh-seclab/TesMla/blob/master/images/s1.JPG)  

### Operation Procedure
- 1. Touch the `Scan` item. Click the` start scan` button and then all BLE devices in range will be shown in a list. Choose the Tesla BLE name "SxxxxxxxxxxxxxxxxcC". Click the `Change MAC` button. 
- 2. Touch the` Fake as App` item. Click the `connect` button and `Change the size of MTU`. Click `get public key of Tesla`. Click `disconnect` button. (optional)
- 3. Touch the' Fake as Tesla' item. Click the `fake broadcast` button.
- 4. Touch the` Fake as App` item. Click the `connect` button and `Change the size of MTU`. Click the `Send data` button. 
- 5. Touch the` Fake as Tesla` item. Click the `fake broadcast` button. 
- 6. Touch the` Fake as App` item. Click the `connect` button and `Change the size of MTU`. Click the `Send data` button. The Tesla Model 3 unlocks the door and can be driven.


About us  
------
Software and Systems Security Team  
Security Lab  
Shanghai Fudan Microelectronics Group Company Limited  
xiexinyi@fmsh.com.cn, jiangkun@fmsh.com.cn, dairui@fmsh.com.cn
