<img src="https://github.com/niedev/RTranslator/blob/master/images/logo_beta_cut.png" width="200">

RTranslator is the world's first open source real-time translation app.

Connect to someone who has the app, connect Bluetooth headphones, put the phone in your pocket and you can have a conversation as if the other person spoke your language.
<br /><br /><br />

![Conversation mode](https://github.com/niedev/RTranslator/blob/master/images/conversation_image_github.png)
<br /><br /><br />
![WalkieTalkie mode and Costs](https://github.com/niedev/RTranslator/blob/master/images/WalkieTalkie_and_Costs_image_github.png)
<br /><br /><br />


<h3>Initial configuration</h3>

First download the latest version of the app apk file from https://github.com/niedev/RTranslator/releases/ and install it.<br />
To use speech recognition and translation (without it the app can do nothing) you need to create a Google Cloud Platform and get a file to associate it with the APIs for the payment (even if, activating the free trial, you have $ 300 credit to use in 1 year in Google Cloud Platform) based on the use of the latter, to get the file follow this tutorial from a computer or using the "desktop site" option on the phone, this is because the Google Cloud
Platform mobile version site does not have some options necessary to complete the tutorial:


<strong>1.</strong> Go to&nbsp;<a style="background-color: #fafafa; font-size: 1rem;" href="http://console.cloud.google.com/" 
target="_blank" rel="noopener noreferrer">console.cloud.google.com</a>&nbsp;and sign in with your Google account or create one.

<strong>2.</strong> Activate the free trial and enter the required data (select the private account type if you do not have a company),
including the credit card details (only for when the free trial will be sold out), to see how to disconnect the card read the point 13.

<strong>3.</strong> Create a new project from the top left (there is already a default project called "My First Project" but that
will give problems for the operations we will perform) by clicking on "My First Project" and then on "NEW PROJECT", the name doesn't
matter.

<strong>4.</strong> From the left pop-up bar select "API and services", then "Dashboard".

<strong>5.</strong> Click on "ENABLE API AND SERVICES" and enable "Cloud Speech-to-Text API" and "Cloud Translation API".

<strong>6.</strong> Also from the left pop-up bar, select "APIs &amp; services" again, then "Credentials".

<strong>7.</strong> Click on "Create credentials", then on "Service account key", fill out the form by creating a new service
account (if it is not already present), choose at will the name and id of the account and clicking on the Role select "Service Usage" 
(at the bottom), then "API Keys Admin", finally select the "Key Type" JSON and press "Create".

<strong>8.</strong> At this point, the key will be generated and downloaded automatically.

<strong>9.</strong> If you used a computer to do these operations, move the key file to your phone (in a random folder).

<strong>10.</strong> Open the RTranslator app and after the initial configuration, click on the three dots at the top right and then
on "APIs Management", then click on the button to attach the APIs Key and select the above file from the list.

<strong>11.</strong> Excellent, from now on you can use RTranslator freely.
<br /><br />


<h3>Account management</h3>


<strong>12.</strong> You must keep the key file safe, because if someone came into its possession it could use Google's API at your expense; keys can be deleted, created, limited, etc. always from&nbsp;<a style="font-size: 1rem; background-color: #fafafa;" href="http://console.cloud.google.com/" target="_blank" rel="noopener noreferrer">console.cloud.google.com</a>, if you lose the key, therefore, it will have to be deleted via the aforementioned site, at which point the key file will no longer be valid, and to use RTranslator you will have to repeat the tutorial from step 6.

<strong>13.</strong> To disconnect the credit card from the account, from the pop-up bar on the left select "Billing", then "Account management", at the top click on "CLOSE BILLING ACCOUNT" and confirm; the API keys from now until you reopen the account (click on "REOPEN BILLING ACCOUNT" instead of the button to close it) will not work, and money will not be deducted from the credit card.

<strong>14.</strong> In general, you can manage everything from the&nbsp;<a style="background-color: #fafafa; font-size: 1rem;" href="http://console.cloud.google.com/" target="_blank" rel="noopener noreferrer">console.cloud.google.com</a>&nbsp;site, visit the website to learn more.
<br /><br />

<strong>N.B.</strong> The cost of the API is around 2-3 dollars per hour, make sure you don't forget the application in background when it is in WalkieTalkie or Conversation mode (just exit from the selected mode by pressing back or the exit button instead of pressing the home button ), moreover the microphone data will be sent to Google servers to be processed in order to obtain the final result (voice recognition or translation), the aforementioned data will not be saved or used for other purposes by google unless you activate the logging in the cloud platform console.
<br /><br />


<h3>APIs prices</h3>

The voice recognition rounds the cost of each request to the nearest multiple of 15 seconds, the average total cost (both translator and voice recognition), without taking into account the rounding, is 2.5 dollars per hour, also for WalkieTalkie must add the language detection costs to the translation, to learn more:

<a href="https://cloud.google.com/speech-to-text/pricing" target="_blank" rel="noopener noreferrer">cloud.google.com/speech-to-text/pricing</a>

<a href="https://cloud.google.com/translate/pricing" target="_blank" rel="noopener noreferrer">cloud.google.com/translate/pricing</a>

The translator uses API Translation v2, voice recognition uses standard models, data logging is disabled by default.&nbsp;If you want
to reduce voice recognition costs you can activate data loggin, to learn more visit&nbsp;<a style="background-color: #fafafa;
font-size: 1rem;" href="https://cloud.google.com/speech-to-text/docs/enable-data-logging" target="_blank" rel="noopener noreferrer">
cloud.google.com/speech-to-text/docs/enable-data-logging</a>.
<br /><br />

<h3>Informations</h3>

Supported languages (excluding variants) are as follows:

Bengali, Czech, Chinese, Korean, Danish, Finnish, French, Japanese, Greek, Hindi, Indonesian, English, Italian, Khmer, Nepalese, Dutch, Polish, Portuguese, Romanian, Russian, Sinhalese, Slovak, Spanish, Sundanese, Swedish, German, Thai, Turkish, Ukrainian, Hungarian, Vietnamese.
<br /><br />

<h3>Privacy</h3>

Privacy is a very important right, that's why RTranslator does not collect any personal data (I don't even have a server), for more information read the <a href="https://github.com/niedev/RTranslator/blob/master/privacy/Privacy_Policy_en.md" target="_blank" rel="noopener noreferrer">privacy policy</a>.

Also with regard to audio data and transcripts sent to Google for translation and speech recognition (only when speaking in WalkieTalkie mode or Conversation mode and the microphone becomes clearer, so only when necessary), the latter are used by Google only to carry out these operations and in no other way.

On the other hand, if data logging for Google Cloud Speech is activated, the audio data sent will be used by Google only to improve its products and services.
For more information read https://cloud.google.com/speech-to-text/docs/data-logging#data-security for Google Cloud Speech and https://cloud.google.com/translate/data-usage for Google Cloud Translation API.
<br /><br />

<h3>Libraries</h3>

RTranslator uses two open source libraries, one for communication between devices based on Bluetooth Low Energy and another for selecting and cropping the profile image from the gallery.

The two libraries are <a href="https://github.com/niedev/BluetoothCommunicator" target="_blank" rel="noopener noreferrer">BluetoothCommunicator</a> and <a href="https://github.com/niedev/GalleryImageSelector" target="_blank" rel="noopener noreferrer">GalleryImageSelector</a> respectively, see their github pages and sample apps for more details.
<br /><br />

<h3>Bugs and problems</h3>
I remember that the app is still a beta, the bugs found are the following:

- for some languages the TTS does not work, reinstall the text-to-speech engine to resolve
- on some devices there are device search problems
- sometimes at the first start the app notify that Bluetooth LE is not supported, if your device supports Bluetooth LE at the next time you start the app the message should no longer appear
<br /><br />

Enjoy your real-time translator
