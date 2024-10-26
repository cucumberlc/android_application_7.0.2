/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.FirmwareUpdater

enum class UpdateResult {
    Success,
    ConcurrentCall,
    TooManyNodes,
    TooBigFirmware,
    UploadFailed
}