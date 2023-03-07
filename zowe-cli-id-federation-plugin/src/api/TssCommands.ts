/**
 * This program and the accompanying materials are made available and may be used, at your option, under either:
 * * Eclipse Public License v2.0, available at https://www.eclipse.org/legal/epl-v20.html, OR
 * * Apache License, version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

import {ImperativeError, TextUtils} from "@zowe/imperative";
import { warn } from "console";
import * as fs from "fs";
import {IIdentity} from "./CsvParser";
import {hasValidLength} from "./ValidateUtil";

export class TssCommands {

    readonly maxLengthMainframeId = 8;
    readonly maxLengthDistributedId = 246;
    readonly maxLengthLabel = 32;

    constructor(
        private registry: string,
        private identities: IIdentity[]
    ) {
    }

    getCommands(): string[] {
        const tssTemplate = fs.readFileSync('src/api/templates/tss.jcl').toString();
        const tssRefreshCommand = fs.readFileSync('src/api/templates/tss_refresh.jcl').toString();

        const tssCommands = this.identities
            .map(identity => this.getCommand(identity, tssTemplate))
            .filter(command => command);

        if (!tssCommands.some(Boolean)) {
            throw new ImperativeError({msg: "Error when trying to create the identity mapping."});
        }
        tssCommands.push(tssRefreshCommand);
        return tssCommands;
    }

    private getCommand(identity: IIdentity, tssTemplate: string): string {
        if(!hasValidLength(identity.mainframeId, this.maxLengthMainframeId)) {
            warn(`The mainframe user ID '${identity.mainframeId}' has exceeded maximum length of ${this.maxLengthMainframeId} characters. ` +
           `Identity mapping for the user '${identity.userName}' has not been created.`);
            return '';
        }

        if(!hasValidLength(identity.distributedId, this.maxLengthDistributedId)) {
            warn(`The distributed user ID '${identity.distributedId}' has exceeded maximum length of ${this.maxLengthDistributedId} characters. ` +
                `Identity mapping for the user '${identity.userName}' has not been created.`);
            return '';
        }

        if(!hasValidLength(identity.userName, this.maxLengthLabel)) {
            warn(`The user name '${identity.userName}' has exceeded maximum length of ${this.maxLengthLabel} characters. ` +
                `Identity mapping for the user '${identity.userName}' has not been created.`);
            return '';
        }

        return TextUtils.renderWithMustache(tssTemplate, {
            mainframe_id: identity.mainframeId.trim(),
            distributed_id: identity.distributedId.trim(),
            registry: this.registry,
            user_name: identity.userName.trim(),
            escape: function() {
                return function(text: string, render: any) {
                    return render(text).replace(/'/g, "''");
                };
            }
        });
    }

}
