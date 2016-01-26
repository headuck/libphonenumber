/*
 * Copyright (C) 2015 The Libphonenumber Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.headuck.phonenumbers;

import com.headuck.phonenumbers.Phonemetadata.PhoneMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of {@link MetadataSource} that reads from a single resource file.
 */
final class SingleFileMetadataSourceImpl implements MetadataSource {

  private static final Logger logger =
      Logger.getLogger(SingleFileMetadataSourceImpl.class.getName());

  private static final String META_DATA_FILE_NAME =
      "/com/headuck/phonenumbers/data/PhoneNumberMetadataSimplified";

  // The metadata file from which region data is loaded.
  private final String fileName;

  // The metadata loader used to inject alternative metadata sources.
  private final MetadataLoader metadataLoader;

    private PhoneMetadataCollection phoneMetaDataCollection = new PhoneMetadataCollection();

  // It is assumed that metadataLoader is not null.
  public SingleFileMetadataSourceImpl(String fileName, MetadataLoader metadataLoader) {
    this.fileName = fileName;
    this.metadataLoader = metadataLoader;
  }

  // It is assumed that metadataLoader is not null.
  public SingleFileMetadataSourceImpl(MetadataLoader metadataLoader) {
    this(META_DATA_FILE_NAME, metadataLoader);
  }

  @Override
  public PhoneMetadata getMetadataForRegion(String regionCode) {
      // The regionCode here will be valid and won't be '001', so we don't need to worry about
      // what to pass in for the country calling code.
      loadMetadataFromFileSync();
      return phoneMetaDataCollection.getRegionMetadata(regionCode);
  }

  @Override
  public PhoneMetadata getMetadataForNonGeographicalRegion(int countryCallingCode) {
      loadMetadataFromFileSync();
      return phoneMetaDataCollection.getNonGeoMetadata(countryCallingCode);
  }

    private void loadMetadataFromFileSync() {
        synchronized (phoneMetaDataCollection) {
            if (!phoneMetaDataCollection.loaded) {
                loadMetadataFromFile();
            }
        }
    }

  // @VisibleForTesting
  void loadMetadataFromFile() {
    InputStream source = metadataLoader.loadMetadata(fileName);
    if (source == null) {
      logger.log(Level.SEVERE, "missing metadata: " + fileName);
      throw new IllegalStateException("missing metadata: " + fileName);
    }

      loadMetadataAndCloseInput(source);
      if (phoneMetaDataCollection.size() == 0) {
        logger.log(Level.SEVERE, "empty metadata: " + fileName);
        throw new IllegalStateException("empty metadata: " + fileName);
      }
  }

  /**
   * Loads the metadata from the given stream and closes the stream afterwards. Any
   * exceptions that occur while reading or closing the stream are ignored.
   *
   * @param source  the non-null stream from which metadata is to be read.
   *
   */
  private void loadMetadataAndCloseInput(InputStream source) {

    try {
        phoneMetaDataCollection.readFrom(source);
    } catch (IOException e) {
      logger.log(Level.WARNING, "error reading input (ignored)", e);
    } finally {
      try {
        source.close();
      } catch (IOException e) {
        logger.log(Level.WARNING, "error closing input stream (ignored)", e);
      }
    }
  }

  private static class PhoneMetadataCollection {
      // true if data is loaded
      public boolean loaded = false;

      // A mapping from a region code to the PhoneMetadata for that region.
      // Note: Synchronization, though only needed for the Android version of the library, is used in
      // all versions for consistency.
      private final Map<String, Integer> regionToMetadataMap =
              Collections.synchronizedMap(new HashMap<String, Integer>());

      // A mapping from a country calling code for a non-geographical entity to the PhoneMetadata for
      // that country calling code. Examples of the country calling codes include 800 (International
      // Toll Free Service) and 808 (International Shared Cost Service).
      // Note: Synchronization, though only needed for the Android version of the library, is used in
      // all versions for consistency.
      private final Map<Integer, Integer> countryCodeToNonGeographicalMetadataMap =
              Collections.synchronizedMap(new HashMap<Integer, Integer>());

      private int[] dataPosition;
      private short[] callingCode;
      private byte[] byteBuf;
      private int numEntries = 0;

      public void readFrom(InputStream source) throws IOException {
          byte header[] = new byte[2];
          // Read number of entries
          int ret = fillByteArray(header, source);

          if (ret != 2) {
              throw new IOException("Error reading data: too short");
          }

          int len = ((header[0] & 0xFF) << 8) + (header[1] & 0xFF);

          if ((len == 0) || (len > 1000)) {   // sanity check
              throw new IOException("Error in source data: invalid number of entries: "+ len);
          }
          // read 3 short ints per entry
          byte[] idxByteBuf = new byte[len*3*2];

          ret = fillByteArray(idxByteBuf, source);
          if (ret != len*3*2) {
              throw new IOException("Error in source data: header index incomplete");
          }

          // Init data arrays
          // pointer into buffer
          dataPosition = new int[len];
          callingCode = new short[len];

          int pos = 0;
          // Retrieve info from header
          for (int i = 0; i < len; i++) {
              // get the key id
              short id = (short) (( idxByteBuf [i*2*3] & 0xFF) * 256 +
                      (idxByteBuf [i*2*3 + 1] & 0xFF));
              short bufLen = (short) (( idxByteBuf [i*2*3 + 2] & 0xFF) * 256 +
                      (idxByteBuf [i*2*3 + 3] & 0xFF));

              if (id > 1000) {
                  // should be two character id
                  String regionCode = "" + (char) (id >>> 8) + (char)(id % 256);
                  regionToMetadataMap.put(regionCode, i);
              } else {
                  countryCodeToNonGeographicalMetadataMap.put ((int)id, i);
              }
              dataPosition[i] = pos;
              pos += bufLen;

              // Calling code and flags
              callingCode[i] = (short) (( idxByteBuf [i*2*3 + 4] & 0xFF) * 256 +
                      (idxByteBuf [i*2*3 + 5] & 0xFF));

          }
          // pos is now the length of the expanded 8-bit buffer
          // calculate the length for 5 bit buffer
          int len5bit = (pos * 5 + 7) / 8;

          byteBuf = new byte[len5bit];
          ret = fillByteArray(byteBuf, source);
          if (ret < len5bit) {
              throw new IOException("Error in source data: main buffer too short: "+ret+"/"+len5bit+" read");
          }
          loaded = true;
          numEntries = len;
      }

      /**
       * Fill a byte array with byte stream from source, return number of bytes read
       * @param byteBuf buffer to fill
       * @param source input stream
       * @return bytes read
       * @throws IOException
       */
      private int fillByteArray(byte[] byteBuf, InputStream source) throws IOException {
          int pos = 0;
          int len = byteBuf.length;
          int ret = 0;
          while ((ret != -1) && (len >0)) {
              ret = source.read(byteBuf, pos, len);
              if (ret != -1) {
                  pos += ret;
                  len -= ret;
              }
          }
            return pos;
      }

    public int size() {
        return numEntries;
    }
    public PhoneMetadata getRegionMetadata(String regionCode) {
        Integer idx = regionToMetadataMap.get(regionCode);
        if (idx != null) {
            PhoneMetadata phoneMetaData = getMetadata(idx);
            phoneMetaData.id = regionCode;
            return phoneMetaData;
        }
        return null;
    }

      public PhoneMetadata getNonGeoMetadata(int countryCode) {
          Integer idx = countryCodeToNonGeographicalMetadataMap.get(countryCode);
          if (idx != null) {
              PhoneMetadata phoneMetaData = getMetadata(idx);
              phoneMetaData.id = PhoneNumberUtilLite.REGION_CODE_FOR_NON_GEO_ENTITY;
              return phoneMetaData;
          }
          return null;
      }

      private PhoneMetadata getMetadata (int idx) {
          PhoneMetadata phoneMetadata = new PhoneMetadata();
          int pos = dataPosition[idx];

          String record = Utils.getCountryRecord(byteBuf, pos);
          int recLen = record.length();
          int fieldCode = -1;
          int lastStartPos = -1;
          boolean start = true;
          boolean completed = false;
          // Scan the string and get the fields
          for (int i = 0; i < recLen; i++) {
              if (start) {
                  fieldCode = record.charAt(i) - 'A';
                  start = false;
                  lastStartPos = i + 1;
              } else {
                  char c = record.charAt(i);
                  if ((c == ';') || (c == '\n')) {
                      if ((c == ';') && (i - 1 >= lastStartPos) && (record.charAt(i - 1) == '\\')) {
                          // escaped ';', skip
                      } else {
                          // end of a field
                          setField(phoneMetadata, fieldCode, Utils.expandRegex(record.substring(lastStartPos, i)));
                          if (c == '\n') {
                              completed = true;
                              break;
                          }
                          start = true;
                      }
                  }
              }
          }
          if (!completed) {
              // incomplete record
              return null;
          }

          // Expand the calling code
          final int FLAG_SAME_MOBILE_FIXED = 1024;
          final int FLAG_MAIN_COUNTRY_FOR_CODE = 2048;
          final int FLAG_LEADING_ZERO_POSSIBLE = 4096;
          final int FLAG_MOBILE_PORTABLE_REGION = 8192;

          int code = callingCode[idx];
          phoneMetadata.sameMobileAndFixedLinePattern = (code & FLAG_SAME_MOBILE_FIXED) != 0;
          phoneMetadata.mainCountryForCode = (code & FLAG_MAIN_COUNTRY_FOR_CODE) != 0;
          phoneMetadata.leadingZeroPossible = (code & FLAG_LEADING_ZERO_POSSIBLE) != 0;
          phoneMetadata.mobileNumberPortableRegion = (code & FLAG_MOBILE_PORTABLE_REGION) != 0;
          phoneMetadata.countryCode = code & 1023;
          return phoneMetadata;
      }

      private void setField(PhoneMetadata phoneMetadata, int code,
                            String regex) {
          switch (code) {
              // phone type
              case 0:
                  phoneMetadata.generalDescPossible = regex;
                  break;
              case 1:
                  phoneMetadata.generalDesc = regex;
                  break;
              case 2:
                  phoneMetadata.fixedLine = regex;
                  break;
              case 3:
                  phoneMetadata.mobile = regex;
                  break;
              case 4:
                  phoneMetadata.tollFree = regex;
                  break;
              case 5:
                  phoneMetadata.premiumRate = regex;
                  break;
              case 6:
                  phoneMetadata.sharedCost = regex;
                  break;
              case 7:
                  phoneMetadata.personalNumber = regex;
                  break;
              case 8:
                  phoneMetadata.voip = regex;
                  break;
              case 21:
                  phoneMetadata.pager = regex;
                  break;
              case 25:
                  phoneMetadata.uan = regex;
                  break;
              case 28:
                  phoneMetadata.voicemail = regex;
                  break;
              // other fields
              case 11:
                  phoneMetadata.internationalPrefix = regex;
                  break;
              case 23:
                  phoneMetadata.leadingDigits = regex;
                  break;
              default:
                  System.out.println("Invalid field code: " + code);
                  break;
          }
      }

  }
    public static class Utils {
        static final byte CODE_D = 11;
        static final byte CODE_OPEN_SQUARE = 12;
        static final byte CODE_CLOSE_SQUARE = 13;
        static final byte CODE_OPEN_BRACKET = 14;
        static final byte CODE_CLOSE_BRACKET = 15;
        static final byte CODE_CHOICE = 16;
        static final byte CODE_COMMA = 17;
        static final byte CODE_RANGE = 18;
        static final byte CODE_BACKSLASH = 19;
        static final byte CODE_OPTION = 20;
        static final byte CODE_SEMICOLON = 21;
        static final byte CODE_SEPARATOR = 31;
        static final byte CODE_TERMINATOR = 0;

        // Expand into original regular expression
        // 1. expand ( to (?:, escaped \( to (
        // 2. replace d with \d
        // 3. following d, replace any #[,#] / #[,#]} with {#[,#} except when d is escaped
        // 4. also unescape \; if any

        public static String expandRegex(String string) {
            StringBuilder sb = new StringBuilder();
            int strlen = string.length();
            boolean inCurly = false;
            boolean afterComma = false;
            boolean afterD = false;
            boolean escape = false;
            for (int i = 0; i < strlen; i++) {
                char c = string.charAt(i);
                if (c == '\\') {
                    escape = true;
                    continue;
                }
                if (Character.isDigit(c)) {
                    if (afterD) {
                        if (escape) {  // should not happen?
                            sb.append('\\');
                            escape = false;
                        }
                        sb.append('{');
                        afterD = false;
                        inCurly = true;
                    }
                } else {
                    if (afterD) afterD = false;
                    if (inCurly) {
                        if (!afterComma && (c==',')) {
                            afterComma = true;
                        } else {
                            if (c != '}') {
                                if (escape) {  // should not happen?
                                    sb.append('\\');
                                    escape = false;
                                }
                                sb.append('}');
                            }
                            inCurly = false;
                            afterComma = false;
                        }
                    }
                }
                if (c == '(') {
                    if (escape) {
                        sb.append("(");   // \( -> (
                        escape = false;
                    } else {
                        sb.append("(?:");
                    }
                } else if (c =='d') {
                    sb.append("\\d");
                    if (!escape) {
                        afterD = true;    // escaped if followed by real digit not in curry
                    } else {
                        escape = false;
                    }
                } else {
                    if (escape) {
                        if ((c != '\\') && (c != ';')) {
                            sb.append('\\');    // \\ -> \  \; -> ; otherwise don't delete \
                        }
                        escape = false;
                    }
                    sb.append(c);
                }

            }
            if (escape) sb.append ('\\');
            if (inCurly) sb.append('}');
            return sb.toString();
        }


        /**
         * Retrieve & expand 5-bit buffer to String at pos (counted at 5 bit length)
         * @param buffer
         * @return String of the record
         */
        private static String getCountryRecord(byte[]buffer, int pos) {
            // Calculate start bit & byte offset
            StringBuilder sb = new StringBuilder();
            byte data;
            int bitpos = pos * 5;
            int bytepos = bitpos / 8;
            final int bufferLen = buffer.length;
            bitpos = bitpos % 8;
            boolean first = true;
            while (true) {
                if (bitpos <= 3) {
                    // retrieve from the buf at bytepos
                    data = (byte) (((buffer[bytepos] & 0xFF) >>> (3 - bitpos)) & 31);
                    bitpos += 5;
                } else {
                    // need to advance to next buf byte
                    if (bitpos < 8) {
                        data = (byte) ((buffer[bytepos] << (bitpos - 3)) & 31);
                    } else {
                        data = 0;
                    }
                    bytepos ++;
                    if (bytepos >= bufferLen) {
                        System.out.println("Unexpected end of buffer");
                        break;
                    }
                    // System.out.println(" " + bytepos + ": " + buffer[bytepos]);
                    data |= (byte) (((buffer[bytepos] & 0xFF) >>> (8 - bitpos + 3)) & 31);
                    bitpos -= 3;
                }
                // process data

                char chr = decodeProcess(first, data);
                if (chr != ' ') {
                    sb.append(chr);
                }
                if (first) {
                    first = false;
                } else {
                    if (data == CODE_TERMINATOR) {
                        break;
                    }
                    if (data == CODE_SEPARATOR) {
                        first = true;
                    }
                }
            }
            return sb.toString();

        }

        static final char[] decodeTable = new char[] {
                'd', '[', ']', '(', ')', '|', ',', '-', '\\', '?', ';'
        };

        private static char decodeProcess(boolean first, byte data) {
            // Return the char representation of data
            if (first) {
                return (char) (data + 'A');
            }
            if ((data >= 1) && (data <= 10)) {
                return (char) ('0' + data - 1);
            }
            if (data == CODE_SEPARATOR) {
                return ';';
            }
            if (data == CODE_TERMINATOR) {
                return '\n';
            }

            int pos = data - CODE_D;
            if ((pos >=0) && (pos < decodeTable.length)) {
                return decodeTable[pos];
            }

            System.out.println("Invalid code: " + data);
            return ' ';
        }
    }
}
