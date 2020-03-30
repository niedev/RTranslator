/*
 * Copyright 2016 Luca Martino.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyFile of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nie.translator.rtranslatordevedition.tools;

import java.io.Serializable;

/**
 * Not used, this is for an eventually encryption
 */
public class EncryptionKey implements Serializable {
    private byte[] key;
    private byte[] iv;

    public EncryptionKey(byte[] key, byte[] iv) {
        this.key = key;
        this.iv = iv;
    }

    public byte[] getKey() {
        return key;
    }

    public byte[] getIv() {
        return iv;
    }

    public void setKey(byte[] key) {
        this.key = key;
    }

    public void setIv(byte[] iv) {
        this.iv = iv;
    }
}
