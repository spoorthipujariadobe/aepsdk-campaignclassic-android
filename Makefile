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


clean:
	  (./code/gradlew -p code clean)

checkstyle:
		(./code/gradlew -p code/campaignclassic checkstyle)

check-format:
		(./code/gradlew -p code/campaignclassic spotlessCheck)
		
format:
		(./code/gradlew -p code/campaignclassic spotlessApply)
		
format-license:
		(./code/gradlew -p code licenseFormat)

unit-test:
		(./code/gradlew -p code/campaignclassic testPhoneDebugUnitTest)

unit-test-coverage:
		(./code/gradlew -p code/campaignclassic createPhoneDebugUnitTestCoverageReport)

functional-test:
		(./code/gradlew -p code/campaignclassic uninstallPhoneDebugAndroidTest)
		(./code/gradlew -p code/campaignclassic connectedPhoneDebugAndroidTest)

functional-test-coverage:
		(./code/gradlew -p code/campaignclassic createPhoneDebugAndroidTestCoverageReport)

javadoc:
		(./code/gradlew -p code/campaignclassic javadocJar)

publish:
		(./code/gradlew -p code/campaignclassic publishReleasePublicationToSonatypeRepository)

assemble-phone:
		(./code/gradlew -p code/campaignclassic assemblePhone)
		
assemble-phone-release:
		(./code/gradlew -p code/campaignclassic assemblePhoneRelease)

assemble-app:
		(./code/gradlew -p code/testapp  assemble)
		
ci-publish-staging: clean assemble-phone-release
		(./code/gradlew -p code/campaignclassic publishReleasePublicationToSonatypeRepository --stacktrace)

ci-publish-main: clean assemble-phone-release
		(./code/gradlew -p code/campaignclassic publishReleasePublicationToSonatypeRepository -Prelease)
		
