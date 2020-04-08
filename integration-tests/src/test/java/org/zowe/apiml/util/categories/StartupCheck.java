/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.util.categories;

/**
 * A category marker for test that should pass before other integration tests
 * to make sure that all services are ready for testing.
 *
 * These tests will run before all other integration tests.
 */
public interface StartupCheck {
}
