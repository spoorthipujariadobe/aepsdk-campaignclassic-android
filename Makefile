#
# Copyright 2019 Adobe. All rights reserved.
# This file is licensed to you under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License. You may obtain a copy
# of the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under
# the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
# OF ANY KIND, either express or implied. See the License for the specific language
# governing permissions and limitations under the License.
#

checkstyle:
		(./code/gradlew -p code/android-campaignclassic-library checkstyle)
		
check-format:
		(./code/gradlew -p code/android-campaignclassic-library ktlintCheck)
		
format:
		(./code/gradlew -p code/android-campaignclassic-library ktlintFormat)
		
format-license:
		(./code/gradlew -p code licenseFormat)

unit-test:
		(./code/gradlew -p code/android-campaignclassic-library testPhoneDebugUnitTest)

unit-test-coverage:
		(./code/gradlew -p code/android-campaignclassic-library createPhoneDebugUnitTestCoverageReport)

functional-test:
		(./code/gradlew -p code/android-campaignclassic-library uninstallPhoneDebugAndroidTest)
		(./code/gradlew -p code/android-campaignclassic-library connectedPhoneDebugAndroidTest)

functional-test-coverage:
		(./code/gradlew -p code/android-campaignclassic-library createPhoneDebugAndroidTestCoverageReport)

javadoc:
		(./code/gradlew -p code/android-campaignclassic-library dokkaJavadoc)

publish:
		(./code/gradlew -p code/android-campaignclassic-library publishReleasePublicationToSonatypeRepository)

assemble-phone:
		(./code/gradlew -p code/android-campaignclassic-library assemblePhone)
		
assemble-phone-release:
		(./code/gradlew -p code/android-campaignclassic-library assemblePhoneRelease)

assemble-app:
		(./code/gradlew -p code/testapp  assemble)
