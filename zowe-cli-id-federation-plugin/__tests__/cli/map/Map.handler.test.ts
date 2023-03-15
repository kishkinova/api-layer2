 /**
 * This program and the accompanying materials are made available and may be used, at your option, under either:
 * * Eclipse Public License v2.0, available at https://www.eclipse.org/legal/epl-v20.html, OR
 * * Apache License, version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

import {ResponseMock} from "../../__src__/ResponseMock";
import {Constants} from "../../../src/api/Constants";

describe("map handler unit tests", () => {

    it("should accept options and return successful message", async () => {
        // Require the handler and create a new instance
        const handlerReq = require("../../../src/cli/map/Map.handler");
        const handler = new handlerReq.default();

        // Vars populated by the mocked function - error should remain undefined.
        let error;
        let logMessage = "";

        // The handler should succeed
        try {
            // Invoke the handler with a full set of mocked arguments and response functions
            await handler.process({
                arguments: {
                    $0: "fake",
                    _: ["fake"],
                    inputFile: "__tests__/__resources__/csv/users.csv",
                    esm: "RACF",
                    registry: "ldap://host:1234"
                },
                response: {
                    console: {
                        log: jest.fn((logArgs) => {
                            logMessage += " " + logArgs;
                        })
                    }
                }
            });
        } catch (e) {
            error = e;
        }

        expect(error).toBeUndefined();
        expect(logMessage).toMatchSnapshot();
    });

    it("throw an error when file does not exist", async () => {
        const handlerReq = require("../../../src/cli/map/Map.handler");
        const handler = new handlerReq.default();

        let error;
        const response = new ResponseMock();
        try {
            await handler.process({
                arguments: {
                    $0: "fake",
                    _: ["fake"],
                    inputFile: "no.csv",
                    esm: "RACF",
                    registry: "ldap://host:1234"
                },
                response: response
            });
        } catch (e) {
            error = e;
        }

        expect(response.exitCode).toBe(Constants.fatalCode);
        expect(error).toBeDefined();
        expect(error.message).toMatchSnapshot();
    });

});
