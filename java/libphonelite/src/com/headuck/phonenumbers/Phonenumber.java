package com.headuck.phonenumbers;

/*
 * Copyright (C) 2010 The Libphonenumber Authors
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

/**
 * Definition of the class representing international telephone numbers. This class is hand-created
 * based on the class file compiled from phonenumber.proto. Please refer to that file for detailed
 * descriptions of the meaning of each field.
 */

import java.io.Serializable;

public final class Phonenumber {
    private Phonenumber() {}
    public enum CountryCodeSource {
        FROM_NUMBER_WITH_PLUS_SIGN,
        FROM_NUMBER_WITH_IDD,
        FROM_NUMBER_WITHOUT_PLUS_SIGN,
        FROM_DEFAULT_COUNTRY
    }
    public static class PhoneNumber implements Serializable {
        private static final long serialVersionUID = 1L;


        public PhoneNumber() {
            countryCodeSource_ = CountryCodeSource.FROM_NUMBER_WITH_PLUS_SIGN;
        }


        // required int32 country_code = 1;
        private boolean hasCountryCode;
        private int countryCode_ = 0;
        public boolean hasCountryCode() { return hasCountryCode; }
        public int getCountryCode() { return countryCode_; }
        public PhoneNumber setCountryCode(int value) {
            hasCountryCode = true;
            countryCode_ = value;
            return this;
        }
        public PhoneNumber clearCountryCode() {
            hasCountryCode = false;
            countryCode_ = 0;
            return this;
        }

        // required uint64 national_number = 2;
        private boolean hasNationalNumber;
        private long nationalNumber_ = 0L;
        public boolean hasNationalNumber() { return hasNationalNumber; }
        public long getNationalNumber() { return nationalNumber_; }
        public PhoneNumber setNationalNumber(long value) {
            hasNationalNumber = true;
            nationalNumber_ = value;
            return this;
        }
        public PhoneNumber clearNationalNumber() {
            hasNationalNumber = false;
            nationalNumber_ = 0L;
            return this;
        }

       // optional bool italian_leading_zero = 4;
        private boolean hasItalianLeadingZero;
        private boolean italianLeadingZero_ = false;
        public boolean hasItalianLeadingZero() { return hasItalianLeadingZero; }
        public boolean isItalianLeadingZero() { return italianLeadingZero_; }
        public PhoneNumber setItalianLeadingZero(boolean value) {
            hasItalianLeadingZero = true;
            italianLeadingZero_ = value;
            return this;
        }
        public PhoneNumber clearItalianLeadingZero() {
            hasItalianLeadingZero = false;
            italianLeadingZero_ = false;
            return this;
        }

        // optional CountryCodeSource country_code_source = 6;
        private boolean hasCountryCodeSource;
        private CountryCodeSource countryCodeSource_;
        public boolean hasCountryCodeSource() { return hasCountryCodeSource; }
        public CountryCodeSource getCountryCodeSource() { return countryCodeSource_; }
        public PhoneNumber setCountryCodeSource(CountryCodeSource value) {
            if (value == null) {
                throw new NullPointerException();
            }
            hasCountryCodeSource = true;
            countryCodeSource_ = value;
            return this;
        }
        public PhoneNumber clearCountryCodeSource() {
            hasCountryCodeSource = false;
            countryCodeSource_ = CountryCodeSource.FROM_NUMBER_WITH_PLUS_SIGN;
            return this;
        }

        // optional int32 number_of_leading_zeros = 8 [default = 1];
        private boolean hasNumberOfLeadingZeros;
        private int numberOfLeadingZeros_ = 1;
        public boolean hasNumberOfLeadingZeros() { return hasNumberOfLeadingZeros; }
        public int getNumberOfLeadingZeros() { return numberOfLeadingZeros_; }
        public PhoneNumber setNumberOfLeadingZeros(int value) {
            hasNumberOfLeadingZeros = true;
            numberOfLeadingZeros_ = value;
            return this;
        }
        public PhoneNumber clearNumberOfLeadingZeros() {
            hasNumberOfLeadingZeros = false;
            numberOfLeadingZeros_ = 1;
            return this;
        }

        // optional string raw_input = 5;
        private boolean hasRawInput;
        private String rawInput_ = "";
        public boolean hasRawInput() { return hasRawInput; }
        public String getRawInput() { return rawInput_; }
        public PhoneNumber setRawInput(String value) {
            if (value == null) {
                throw new NullPointerException();
            }
            hasRawInput = true;
            rawInput_ = value;
            return this;
        }
        public PhoneNumber clearRawInput() {
            hasRawInput = false;
            rawInput_ = "";
            return this;
        }



        public final PhoneNumber clear() {
            clearCountryCode();
            clearNationalNumber();
            clearItalianLeadingZero();
            clearNumberOfLeadingZeros();
            clearRawInput();
            clearCountryCodeSource();
            return this;
        }

        public PhoneNumber mergeFrom(PhoneNumber other) {
            if (other.hasCountryCode()) {
                setCountryCode(other.getCountryCode());
            }
            if (other.hasNationalNumber()) {
                setNationalNumber(other.getNationalNumber());
            }
            if (other.hasItalianLeadingZero()) {
                setItalianLeadingZero(other.isItalianLeadingZero());
            }
            if (other.hasNumberOfLeadingZeros()) {
                setNumberOfLeadingZeros(other.getNumberOfLeadingZeros());
            }
            if (other.hasRawInput()) {
                setRawInput(other.getRawInput());
            }
            if (other.hasCountryCodeSource()) {
                setCountryCodeSource(other.getCountryCodeSource());
            }
            return this;
        }

        public boolean exactlySameAs(PhoneNumber other) {
            if (other == null) {
                return false;
            }
            if (this == other) {
                return true;
            }
            return (countryCode_ == other.countryCode_ && nationalNumber_ == other.nationalNumber_ &&
                    italianLeadingZero_ == other.italianLeadingZero_ &&
                    numberOfLeadingZeros_ == other.numberOfLeadingZeros_ &&
                    rawInput_.equals(other.rawInput_)&& countryCodeSource_ == other.countryCodeSource_);
        }

        @Override
        public boolean equals(Object that) {
            return (that instanceof PhoneNumber) && exactlySameAs((PhoneNumber) that);
        }

        @Override
        public int hashCode() {
            // Simplified rendition of the hashCode function automatically generated from the proto
            // compiler with java_generate_equals_and_hash set to true. We are happy with unset values to
            // be considered equal to their explicitly-set equivalents, so don't check if any value is
            // unknown. The only exception to this is the preferred domestic carrier code.
            int hash = 41;
            hash = (53 * hash) + getCountryCode();
            hash = (53 * hash) + Long.valueOf(getNationalNumber()).hashCode();
            hash = (53 * hash) + (isItalianLeadingZero() ? 1231 : 1237);
            hash = (53 * hash) + getNumberOfLeadingZeros();
            hash = (53 * hash) + getRawInput().hashCode();
            hash = (53 * hash) + getCountryCodeSource().hashCode();
            return hash;
        }

        @Override
        public String toString() {
            StringBuilder outputString = new StringBuilder();
            outputString.append("Country Code: ").append(countryCode_);
            outputString.append(" National Number: ").append(nationalNumber_);
            if (hasItalianLeadingZero() && isItalianLeadingZero()) {
                outputString.append(" Leading Zero(s): true");
            }
            if (hasNumberOfLeadingZeros()) {
                outputString.append(" Number of leading zeros: ").append(numberOfLeadingZeros_);
            }
            if (hasCountryCodeSource()) {
                outputString.append(" Country Code Source: ").append(countryCodeSource_);
            }
            return outputString.toString();
        }
    }
}
