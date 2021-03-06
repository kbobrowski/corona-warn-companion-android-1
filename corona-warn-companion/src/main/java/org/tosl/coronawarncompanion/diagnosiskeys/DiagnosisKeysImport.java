/*
 * Corona-Warn-Companion. An app that shows COVID-19 Exposure Notifications details.
 * Copyright (C) 2020  Michael Huebler <corona-warn-companion@tosl.org> and other contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.tosl.coronawarncompanion.diagnosiskeys;

import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import com.google.protobuf.InvalidProtocolBufferException;

import org.tosl.coronawarncompanion.BuildConfig;
import org.tosl.coronawarncompanion.R;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class DiagnosisKeysImport {

    private static final String TAG = "DiagnosisKeys";

    private DiagnosisKeysProtos.TemporaryExposureKeyExport dkImport = null;

    public DiagnosisKeysImport(byte[] exportDotBin, Context context) {
        String header = "EK Export v1    ";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        if (BuildConfig.DEBUG && headerBytes.length != 16) {
            throw new AssertionError("Invalid header string in source code!");
        }
        if (Arrays.equals(Arrays.copyOf(exportDotBin, 16), headerBytes)) {
            try {
                dkImport = DiagnosisKeysProtos.TemporaryExposureKeyExport.parseFrom(Arrays.copyOfRange(exportDotBin, 16, exportDotBin.length));
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        } else {
            Log.e(TAG, "Invalid Header: export.bin does not start with 'EK Export v1'");
            CharSequence text = context.getResources().getString(R.string.error_download_invalid_key_file_header);
            Toast toast = Toast.makeText(context, text, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
        }
    }

    public List<DiagnosisKeysProtos.TemporaryExposureKey> getDiagnosisKeys() {
        if (dkImport != null) {
            return dkImport.getKeysList();
        } else {
            return null;
        }
    }
}
