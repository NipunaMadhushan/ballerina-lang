//  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
//  WSO2 Inc. licenses this file to you under the Apache License,
//  Version 2.0 (the "License"); you may not use this file except
//  in compliance with the License.
//  You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

public type Foo record {
    string name;
    Foo[] x?;
}[];

public function getFooName(Foo f) returns string => f[0].name;

public function testPublicFunctionWithRecursiveArrayTypedParam() {
    Foo foo = [{name: "Foo"}];
    assertEquality("Foo", getFooName(foo));
}

function assertEquality(anydata expected, anydata actual) {
    if expected == actual {
        return;
    }
    panic error(string `expected '${expected.toString()}', found '${actual.toString()}'`);
}