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

Steps: ::

    # compile the app
    ./gradlew assemble

    # magic bs
    zipalign -v -p 4 \
        app/build/outputs/apk/release/app-release-unsigned.apk \
        app-release-unsigned-aligned.apk

    # Create a key to sign the APK with
    keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-alias

    # Sign the APK
    apksigner sign --ks my-release-key.jks \
        --out streckbryggan.apk \
        app-release-unsigned-aligned.apk
