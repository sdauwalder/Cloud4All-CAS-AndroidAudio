Cloud4All---Mobile Sensing Platform
================================

Description
-----------

The Mobile Sensing Platform (CAS) developed in Java (android) provides a platform that senses the ambient noise and luminosity, and reports it to the CAS. The app itself is a manager of a service that runs in background, sending continuously the data configured by the app.


License
-------

This project has been developed by Eurecat eHealth department. Device reporter is shared under New BSD license. This project folder includes a license file. You can share and distribute this project and built software using New BSD license. Please, send any feedback to http://www.cloud4all.info


Installation
------------

Download de apk file and install it to a android device.


Configuration
-------------

In the main interface, the button "START" starts the background service that continuously sends data, and the "STOP" button stops it. Just underneath, visual feedback of the sound and light data is showed, also alowing the enabling/disabling of the sensors.
In Settings, different parameters can be configured: 
- **Send Interval**: Interval in seconds in which the sensor data is sent to the CAS. Light and sound data is send at the same time.
- **CAS Sound URL**: CAS URL containing the sensor id where the sound data will be sent
- **Sound Maximum**: Sound data send to CAS is 0, 1 or 2 depending if the ambient sound is low, normal or high. This parameter lets you configure the highest value, taking into account that raw sound values are from 0 to 32767.
- **CAS Light URL**: CAS URL containing the sensor id where the light data will be sent
- **Light Maximum**: The light data sent to the CAS is the light value in lux. Therefore this value is only to adjust the light progress bar in the main interface. 