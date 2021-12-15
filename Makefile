.PHONY: default help release clean
.SILENT: help release-key.jks

default: help

help:
	echo "usage:"
	echo "  make release"
	echo "  make clean"

clean:
	rm -f streckbryggan.apk*
	rm -f app-release-unsigned-aligned.apk
	./gradlew clean

release: streckbryggan.apk

release-key.jks:
	echo "$@ not found, please consult the README"
	exit 1

streckbryggan.apk: release-key.jks app-release-unsigned-aligned.apk
	apksigner sign --ks release-key.jks --out $@ app-release-unsigned-aligned.apk

app/build/outputs/apk/release/app-release-unsigned.apk:
	./gradlew assemble

app-release-unsigned-aligned.apk: app/build/outputs/apk/release/app-release-unsigned.apk
	zipalign -v -p 4 \
	    app/build/outputs/apk/release/app-release-unsigned.apk \
	    app-release-unsigned-aligned.apk

