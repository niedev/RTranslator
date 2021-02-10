<img src="https://github.com/niedev/RTranslator/blob/master/images/logo_beta_cut.png" width="280">

RTranslator is the world's first open source real-time translation app.

Connect to someone who has the app, connect Bluetooth headphones, put the phone in your pocket and you can have a conversation as if the other person spoke your language.
<br /><br /><br />

![Conversation mode](https://github.com/niedev/RTranslator/blob/master/images/conversation_image_github.png)
<br /><br /><br />
![WalkieTalkie mode and Costs](https://github.com/niedev/RTranslator/blob/master/images/WalkieTalkie_and_Costs_image_github.png)
<br /><br /><br />

<h3>Conversation mode</h3>

The Conversation mode is the main feature of RTranslator. In this mode, you can connect with another phone that uses this app. If the user accepts your connection request:

- When you talk, your phone (or the Bluetooth headset, if connected) will capture the audio.

- The audio captured will be converted into text and sent to the interlocutor's phone.

- The interlocutors' phone will translate the text received into his language.

- The interlocutors' phone will convert the translated text into audio and will reproduce it from its speaker (or by the Bluetooth headset of the interlocutor, if connected to his phone). 

All this in both directions.

Each user can have more than one interlocutor so that you can translate conversations between more than two people and in any combination.
<br /><br />

<h3>WalkieTalkie mode</h3>

If conversation mode is useful for having a long conversation with someone, this mode instead is designed for quick conversations, such as asking for information on the street or talking to a shop assistant.

This mode only translates conversations between two people, it doesn't work with Bluetooth headsets, and you have to talk in turns. It's not a real simultaneous translation, but it can work with only one phone.

In this mode, the smartphone microphone will listen in two languages (selectable in the same screen of the walkie talkie mode) simultaneously. <br />
The app will detect in which language the interlocutor is speaking, translate the audio into the other language, convert the text into audio, and then reproduce it from the phone speaker. When the TTS has finished, it will automatically resume listening.
<br /><br />

<h3>General</h3>

Both translation and speech recognition use Google's APIs to ensure the best possible quality.

Also, RTranslator works even in the background, with the phone on standby or when using other apps (only when you use Conversation or WalkieTalkie modes).

<a href='https://play.google.com/store/apps/details?id=nie.translator.rtranslatordevedition&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' width="200"/></a>
<a href="https://www.producthunt.com/posts/rtranslator?utm_source=badge-featured&utm_medium=badge&utm_souce=badge-rtranslator" target="_blank"><img src="https://api.producthunt.com/widgets/embed-image/v1/featured.svg?post_id=274849&theme=light" alt="RTranslator - World's first open-source simultaneous translation app. | Product Hunt" style="width: 250px; height: 54px;" width="250" height="77"/></a>

<br />
<h3>Initial configuration</h3>

To use speech recognition and translation (without it, the app can do nothing), you need to create a Google Cloud Platform account and create and get a file to associate the account with the APIs for the payment based on the latter's use. If you create the account for the first time, activating the free trial, you will have $ 300 credit to use for one year in Google Cloud Platform.

To create the account and get the file follow this tutorial from a computer or using the "desktop site" option on the phone, this is because the Google Cloud Platform mobile version site does not have some options necessary to complete the tutorial.

First, download the latest version of the app apk file from https://github.com/niedev/RTranslator/releases/ or Google Play Store and install it. Then follow these passages:
<br /><br />

<strong>1.</strong> Go to&nbsp;<a style="background-color: #fafafa; font-size: 1rem;" href="http://console.cloud.google.com/" 
target="_blank" rel="noopener noreferrer">console.cloud.google.com</a>&nbsp;and sign in with your Google account or create one.

<strong>2.</strong> Activate the free trial and enter the required data (select the private account type if you do not have a company),
including the credit card details (only for when the free trial will be sold out), to see how to disconnect the card read the point 13.

<strong>3.</strong> Create a new project from the top left (there is already a default project called "My First Project" but that
will give problems for the operations we will perform) by clicking on "My First Project" and then on "NEW PROJECT", the name doesn't
matter.

<strong>4.</strong> From the left pop-up bar, select "API and services", then "Dashboard".

<strong>5.</strong> Click on "ENABLE API AND SERVICES" and enable "Cloud Speech-to-Text API" and "Cloud Translation API".

<strong>6.</strong> Also from the left pop-up bar, select "APIs &amp; services" again, then "Credentials".

<strong>7.</strong> Click on "Create credentials", then on "Service account key", fill out the form by creating a new service
account (if it is not already present), choose at will the name and id of the account and clicking on the Role select "Service Usage" 
(at the bottom), then "API Keys Admin", finally select the "Key Type" JSON and press "Create".

<strong>8.</strong> At this point, the key will be generated and downloaded automatically.

<strong>9.</strong> If you used a computer to do these operations, move the key file to your phone (in a random folder).

<strong>10.</strong> Open the RTranslator app and after the initial configuration, click on the three dots at the top right and then
on "APIs Management", then click on the button to attach the APIs Key and select the above file from the list.

<strong>11.</strong> Excellent, from now on, you can use RTranslator freely.
<br /><br />


<h3>Account management</h3>


<strong>12.</strong> You must keep the key file safe because if someone came into its possession, he could use Google's API at your expense; keys can be deleted, created, limited, etc. always from&nbsp;<a style="font-size: 1rem; background-color: #fafafa;" href="http://console.cloud.google.com/" target="_blank" rel="noopener noreferrer">console.cloud.google.com</a>. If you lose the key, therefore, it will have to be deleted via the site mentioned above. At that point, the key file will no longer be valid, and to use RTranslator you will have to repeat the tutorial from step 6.

<strong>13.</strong> To disconnect the credit card from the account, from the pop-up bar on the left select "Billing", then "Account management", at the top click on "CLOSE BILLING ACCOUNT" and confirm; the API keys from now until you reopen the account (click on "REOPEN BILLING ACCOUNT" instead of the button to close it) will not work, and money will not be deducted from the credit card.

<strong>14.</strong> In general, you can manage everything from the&nbsp;<a style="background-color: #fafafa; font-size: 1rem;" href="http://console.cloud.google.com/" target="_blank" rel="noopener noreferrer">console.cloud.google.com</a>&nbsp;site. Visit the website to learn more.
<br /><br />

<strong>N.B.</strong> The cost of the API is around 2-3 dollars per hour. Make sure you don't forget the application in the background when it is in WalkieTalkie or Conversation mode (just exit from the selected mode by pressing back or the exit button instead of pressing the home button ). Moreover, the microphone data will be sent to Google servers to be processed in order to obtain the final result (voice recognition or translation). The data mentioned above will not be saved or used for other purposes by google unless you activate the logging in the cloud platform console. For more information about it, read the Privacy section below.
<br /><br />


<h3>APIs prices</h3>

The voice recognition rounds the cost of each request to the nearest multiple of 15 seconds. The average total cost (both translator and voice recognition), without taking into account the rounding, is 2.5 dollars per hour. Also for WalkieTalkie must add the language detection costs to the translation. To learn more:

<a href="https://cloud.google.com/speech-to-text/pricing" target="_blank" rel="noopener noreferrer">cloud.google.com/speech-to-text/pricing</a>

<a href="https://cloud.google.com/translate/pricing" target="_blank" rel="noopener noreferrer">cloud.google.com/translate/pricing</a>

The translator uses API Translation v2; voice recognition uses standard models, data logging is disabled by default. If you want to reduce voice recognition costs, you can activate data logging. To learn more, visit &nbsp;<a style="background-color: #fafafa;
font-size: 1rem;" href="https://cloud.google.com/speech-to-text/docs/enable-data-logging" target="_blank" rel="noopener noreferrer">
cloud.google.com/speech-to-text/docs/enable-data-logging</a>.
<br /><br />

<h3>Supported languages</h3>

The languages supported (excluding variants) are as follows:

Bengali, Czech, Chinese, Korean, Danish, Finnish, French, Japanese, Greek, Hindi, Indonesian, English, Italian, Khmer, Nepalese, Dutch, Polish, Portuguese, Romanian, Russian, Sinhalese, Slovak, Spanish, Sundanese, Swedish, German, Thai, Turkish, Ukrainian, Hungarian, Vietnamese.
<br /><br />

<h3>Privacy</h3>

Privacy is a fundamental right. That's why RTranslator does not collect any personal data (I don't even have a server). For more information, read the <a href="https://github.com/niedev/RTranslator/blob/master/privacy/Privacy_Policy_en.md" target="_blank" rel="noopener noreferrer">privacy policy</a>.

Concerning audio data and transcripts sent to Google for translation and speech recognition, they are sent only when speaking in WalkieTalkie mode or Conversation mode and the microphone becomes clearer, so only when necessary. Also, the data are used by Google only to carry out these operations and in no other way.

On the other hand, if data logging for Google Cloud Speech is activated, the audio data sent will be used by Google only to improve its products and services. For more information, read  https://cloud.google.com/speech-to-text/docs/data-logging#data-security for Google Cloud Speech and https://cloud.google.com/translate/data-usage for Google Cloud Translation API.
<br /><br />

<h3>Libraries</h3>

RTranslator uses three open-source libraries, one for communication between devices, another for selecting and cropping the profile image from the gallery and another for the cost chart.

The three libraries are <a href="https://github.com/niedev/BluetoothCommunicator" target="_blank" rel="noopener noreferrer">BluetoothCommunicator</a>, <a href="https://github.com/niedev/GalleryImageSelector" target="_blank" rel="noopener noreferrer">GalleryImageSelector</a> and [GraphView](https://github.com/jjoe64/GraphView) respectively. See their GitHub pages and sample apps for more details.
<br /><br />

<h3>Bugs and problems</h3>
I remember that the app is still a beta. The bugs found are the following:

- For some languages, the TTS does not work. Reinstall the text-to-speech engine to resolve.
- On some devices, there are device search problems.
- Sometimes at the first start, the app notifies that Bluetooth LE is not supported. If your device supports Bluetooth LE, the next time you start the app, the message should no longer appear.
<br /><br />

Enjoy your simultaneous translator.
