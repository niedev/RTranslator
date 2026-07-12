**If you have trouble with the initial download of app's AI models and prefer to do it from a computer and manually insert the models into the app** (sideloading)**, starting with RTranslator version 2.0.3 you can do so by following these steps:**

- **Download RTranslator version 2.0.3 or higher** (if you have already installed it, reset it).

- **Download all the ".onnx" files from the assets of [this release](https://github.com/niedev/RTranslator/releases/tag/2.0.0) in the way you prefer**.<br />
  These files are the neural network models that the app normally downloads on the first launch.

- **Launch the app, accept notification permissions** (notification will only be used to show when the app is running in the background during Conversation or WalkieTalkie mode)**, and close the app** (without continuing).<br />
  In this step, if they are not already present, the app will create the folders where we will insert the AI ​​models.

- **Connect your smartphone to your computer**.<br />
  If you had already connected your phone to the computer, unplug it and plug it back in, because otherwise, even with refresh, the folders just created by RTranslator, in which to insert the models, will not be displayed.

- **Using your computer, insert the AI model files downloaded before into your phone's folder "Android/data/nie.translator.rtranslator/files"**. <br />
  If you have an sd card and you don't know whether to look in the internal memory or the sd card, check if this folder is present in the internal memory and if inside "files" there is a readme file, if there is not then look in the sd card.

- **Once the transfer is finished, open RTranslator and continue with the configuration. Once you get to the download screen, instead of downloading the models, the latter will be extracted directly from the folder where we inserted them before and their integrity will be verified** (a real download starts only for the damaged model files).<br /><br />

**Once finished, the files we placed in the previous folder will disappear** (they have been transferred to the app's internal private memory)**, and the app will work normally**.
<br /><br />
**N.B.** A computer is needed to do this operation because the latest versions of Android limit access to the "Android/" folder from the phone itself, but not from a computer.
