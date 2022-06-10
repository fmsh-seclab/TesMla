![](https://github.com/fmsh-seclab/TesMla/blob/master/images/R-C.png)
TesMla
========

Introduction
-------
This app implements a **man in the middle attack** to Tesla Model 3.

Requirements
-----
All for Android. We test this app on a customized Google Pixel 5A Android Device.  
- Following the instructions on Google android source websites, downloaded the android source code, and modified the definition `BLE_LOCAL_PRIVACY_ENABLED` from True to False, disabled random Bluetooth Devices Address (BD_ADDR) features during Bluetooth advertising. 
- Based on the reverse engineering of Pixel 5A’s vendor firmware, we found the best way is to set the `ro.vendor.bt.boot.macaddr` property through an ADB shell command.

**Note:** This repo is currently a bit preliminary, we will update related weblink and documentation latter.

User manual
------  
### Interface
Bootom navigation bars display three destinations at the bottom of a sceen: Fake as Tesla, Scan and Fake as app.  
**Fake as Tesla:**
- Broadcast: Broadcast with specific BLE name and additional data.
- BLE Server: It provides a service with three characteristics.  It will record and response automatically.

	| Service | UUID | Property| Description|
	| ------- | ------- |---------|----|
	| characteristic 1 | 00000212-b2d1-43f0-9b88-960cebf8b91e|  Write |Receive data from peer device|
	| characteristic 2 | 00000213-b2d1-43f0-9b88-960cebf8b91e|  Indicate |Send data to peer device|
	| characteristic 3 | 00000214-b2d1-43f0-9b88-960cebf8b91e|  Read |Version Information|

**Scan:**
- Scan surroundings: Search BLE devices in range and show their BLE names and addresses in list.  
- Change MAC address: Change local MAC address to specific one. This function may not work if you use an offical Android device which version higher than Android 5.
**Fake as app:**
- Connect: Connect to device with specific MAC address
- Change MTU Size: Change MTU size to 256 bytes
- BLE Client: Send write request to server. Parse indication from server. It will record and response automatically.  
- 
![](https://github.com/fmsh-seclab/TesMla/blob/master/images/s1.JPG)  

### Operation Procedure
- 1. Touch `Scan` item. Click `start scan` button. Choose the Tesla BLE name "SxxxxxxxxxxxxxxxxcC". Click `Change MAC` button. (To get broadcast data and MAC)
- 2. Touch `Fake as App` item. Click `connect` button and `Change size of MTU`. Click `get public key of Tesla`. Click `disconnect` button. (To get pk)
- 3. Touch 'Fake as Tesla' item. Click `fake broadcast` button. (To get first attestation)
- 4. Touch `Fake as App` item. Click `connect` button and `Change size of MTU`. Click  `Send data` button. (To get token)
- 5. Touch `Fake as Tesla` item. Click `fake broadcast` button.  (To get two attestations)
- 6. Touch `Fake as App` item. Click `connect` button and `Change size of MTU`. Click  `Send data` button. The Tesla Model 3 unlocks the door and can be driven.


About us  
------
Security Lab  
Shanghai Fudan Microelectronics Group Company Limited 

