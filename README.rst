StreckBryggan
=============

An android-application which bridges Strecklistan_ with iZettle POS using their SDK_.

.. _Strecklistan: https://github.com/hulthe/strecklistan/
.. _SDK: https://github.com/iZettle/sdk-android/

*TODO: add more stuff here*

How to get started
------------------

- Get an iZettle developer account

- Open the project in android studio

- Copy the ``secrets.gradle.example`` file and fill in the required variables
  from strecklistan, github, and your iZettle developer account.


How to compile release APK
--------------------------

First, set up a key store ::

    keytool -genkey -v -keystore release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-alias

Then, run the makefile: ::

    make release

Or, you can go through the steps manually: ::

    # compile the app
    ./gradlew assemble

    # magic bs
    zipalign -v -p 4 \
        app/build/outputs/apk/release/app-release-unsigned.apk \
        app-release-unsigned-aligned.apk

    # Sign the APK
    apksigner sign --ks release-key.jks \
        --out streckbryggan.apk \
        app-release-unsigned-aligned.apk
