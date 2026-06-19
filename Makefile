GRADLEW := ./gradlew
APK := app/build/outputs/apk/release/rpcsx-release.apk

.PHONY: help test release

help:
	@printf '%s\n' \
		'make test     Run app unit tests' \
		'make release  Build release APK'

test:
	$(GRADLEW) :app:testDebugUnitTest

release:
	$(GRADLEW) assembleRelease
	@printf 'Built %s\n' "$(APK)"
