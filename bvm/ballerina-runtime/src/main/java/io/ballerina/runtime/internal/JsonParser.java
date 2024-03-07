/*
 * Copyright (c) 2024, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.runtime.internal;

import io.ballerina.runtime.api.PredefinedTypes;
import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.creators.ErrorCreator;
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.flags.SymbolFlags;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.Field;
import io.ballerina.runtime.api.types.IntersectionType;
import io.ballerina.runtime.api.types.MapType;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.types.TableType;
import io.ballerina.runtime.api.types.TupleType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.utils.JsonUtils;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BListInitialValueEntry;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BRefValue;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.internal.commons.TypeValuePair;
import io.ballerina.runtime.internal.errors.ErrorCodes;
import io.ballerina.runtime.internal.errors.ErrorHelper;
import io.ballerina.runtime.internal.errors.ErrorReasons;
import io.ballerina.runtime.internal.regexp.RegExpFactory;
import io.ballerina.runtime.internal.types.BArrayType;
import io.ballerina.runtime.internal.types.BIntersectionType;
import io.ballerina.runtime.internal.types.BMapType;
import io.ballerina.runtime.internal.types.BRecordType;
import io.ballerina.runtime.internal.values.ArrayValue;
import io.ballerina.runtime.internal.values.ArrayValueImpl;
import io.ballerina.runtime.internal.values.DecimalValue;
import io.ballerina.runtime.internal.values.MapValueImpl;
import io.ballerina.runtime.internal.values.ReadOnlyUtils;
import io.ballerina.runtime.internal.values.TableValueImpl;
import io.ballerina.runtime.internal.values.TupleValueImpl;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.ballerina.runtime.api.creators.ErrorCreator.createError;
import static io.ballerina.runtime.api.utils.JsonUtils.NonStringValueProcessingMode.FROM_JSON_DECIMAL_STRING;
import static io.ballerina.runtime.api.utils.JsonUtils.NonStringValueProcessingMode.FROM_JSON_FLOAT_STRING;
import static io.ballerina.runtime.internal.ErrorUtils.createConversionError;
import static io.ballerina.runtime.internal.ValueUtils.createRecordValueWithDefaultValues;

/**
 * This class represents a {@link InputStream} parser which creates a value of the given target type
 * which should be a subtype of {@link io.ballerina.runtime.api.types.AnydataType} type. The {@link InputStream} should
 * only contain a sequence of characters that can be parsed as {@link io.ballerina.runtime.api.types.JsonType},
 * otherwise a {@link BError} is thrown.
 *
 * @since 2201.9.0
 */
public class JsonParser {

    private static final ThreadLocal<StreamStateMachine> tlStateMachine =
            ThreadLocal.withInitial(StreamStateMachine::new);

    private JsonParser() {
    }

    /**
     * Parses the contents in the given {@link InputStream} and returns a value of the given target type.
     *
     * @param in input stream which contains the content
     * @return value of the given target type
     * @throws BError for any parsing error
     */
    public static Object parse(InputStream in, Type targetType) throws BError {
        return parse(in, Charset.defaultCharset().name(), targetType);
    }

    /**
     * Parses the contents in the given {@link InputStream} and returns a value of the given target type.
     *
     * @param in          input stream which contains the content
     * @param charsetName the character set name of the input stream
     * @return value of the given target type
     * @throws BError for any parsing error
     */
    public static Object parse(InputStream in, String charsetName, Type targetType) throws BError {
        try {
            return parse(new InputStreamReader(new BufferedInputStream(in), charsetName), targetType);
        } catch (IOException e) {
            throw ErrorCreator.createError(StringUtils.fromString(("error in parsing input stream: "
                                                                   + e.getMessage())));
        }
    }

    /**
     * Parses the contents in the given string and returns a json.
     *
     * @param jsonStr the string which contains the JSON content
     * @return JSON structure
     * @throws BError for any parsing error
     */
    public static Object parse(String jsonStr) throws BError {
        return JsonParser.parse(jsonStr, PredefinedTypes.TYPE_JSON);
    }

    /**
     * Parses the contents in the given string and returns a value of the given target type.
     *
     * @param str the string which contains the content
     * @return value of the given target type
     * @throws BError for any parsing error
     */
    public static Object parse(String str, Type targetType) throws BError {
        return parse(new StringReader(str), targetType);
    }

    /**
     * Parses the contents in the given {@link Reader} and a value of the given target type.
     *
     * @param reader reader which contains the content
     * @param mode    the mode to use when processing numeric values
     * @return value of the given target type
     * @throws BError for any parsing error
     */
    public static Object parse(Reader reader, Type targetType, JsonUtils.NonStringValueProcessingMode mode)
            throws BError {
        StreamStateMachine sm = tlStateMachine.get();
        try {
            sm.addTargetType(targetType);
            StreamStateMachine.mode = mode;
            return sm.execute(reader);
        } finally {
            // Need to reset the state machine before leaving. Otherwise, references to the created
            // values will be maintained and the java GC will not happen properly.
            sm.reset();
            tlStateMachine.remove();
        }
    }

    /**
     * Parses the contents in the given {@link Reader} into a value of JSON type.
     *
     * @param reader reader which contains the content
     * @param mode   the mode to use when processing numeric values
     * @return value of the JSON type
     * @throws BError for any parsing error
     */
    public static Object parse(Reader reader, JsonUtils.NonStringValueProcessingMode mode) {
        return parse(reader, getTargetType(mode), mode);
    }

    /**
     * Parses the contents in the given {@link Reader} and a value of the given target type.
     *
     * @param reader reader which contains the content
     * @return value of the given target type
     * @throws BError for any parsing error
     */
    public static Object parse(Reader reader, Type targetType) throws BError {
        return parse(reader, targetType, JsonUtils.NonStringValueProcessingMode.FROM_JSON_STRING);
    }

    private static Type getTargetType(JsonUtils.NonStringValueProcessingMode mode) {
        Type targetType;
        if (mode == FROM_JSON_DECIMAL_STRING) {
            targetType = PredefinedTypes.TYPE_JSON_DECIMAL;
        } else if (mode == FROM_JSON_FLOAT_STRING) {
            targetType = PredefinedTypes.TYPE_JSON_FLOAT;
        } else {
            targetType = PredefinedTypes.TYPE_JSON;
        }
        return targetType;
    }

    /**
     * Represents the state machine used for input stream parsing.
     */
    private static class StreamStateMachine extends StateMachine {

        private static final String UNSUPPORTED_TYPE = "unsupported type: ";
        private static final String ARRAY_SIZE_MISMATCH = "array size is not enough for the provided values";
        private static final String TUPLE_SIZE_MISMATCH = "tuple size is not enough for the provided values";
        private static final String UNEXPECTED_END_OF_THE_INPUT_STREAM = "unexpected end of the input stream";
        private static final String UNRECOGNIZED_TOKEN = "unrecognized token '";

        // targetTypes list will always have effective referred types because we add only the implied types
        // if the target type is union we put the union type inside targetTypes list and do not add more types,
        // and we create a json value and convert to the target type
        // json, finite, anydata types will be handled the same way as union types, but they do not need conversion
        List<Type> targetTypes = new ArrayList<>();
        List<Integer> listIndices = new ArrayList<>(); // we keep only the current indices of arrays and tuples
        private int nodesStackSizeWhenUnionStarts = -1; // when we come across a union target type we set this value
        private static JsonUtils.NonStringValueProcessingMode mode =
                JsonUtils.NonStringValueProcessingMode.FROM_JSON_STRING;

        StreamStateMachine() {
            super("input stream", new FieldNameState(), new StringValueState(), new StringFieldValueState(),
                    new StringArrayElementState());
        }

        public void reset() {
            super.reset();
            this.targetTypes.clear();
            this.nodesStackSizeWhenUnionStarts = -1;
            this.listIndices.clear();
        }

        private void addTargetType(Type type) {
            this.targetTypes.add(TypeUtils.getImpliedType(type));
        }

        private static ParserException getConversionError(Type targetType, String inputValue) {
            return new ParserException("value '" + inputValue + "' cannot be converted to '" + targetType + "'");
        }

        private static Object convertValues(Type targetType, String inputValue) throws ParserException {
            switch (targetType.getTag()) {
                case TypeTags.INT_TAG, TypeTags.SIGNED32_INT_TAG, TypeTags.SIGNED16_INT_TAG, TypeTags.SIGNED8_INT_TAG,
                        TypeTags.UNSIGNED32_INT_TAG, TypeTags.UNSIGNED16_INT_TAG, TypeTags.UNSIGNED8_INT_TAG -> {
                    try {
                        return Long.parseLong(inputValue);
                    } catch (NumberFormatException e) {
                        throw getConversionError(targetType, inputValue);
                    }
                }
                case TypeTags.DECIMAL_TAG -> {
                    try {
                        return new DecimalValue(inputValue);
                    } catch (NumberFormatException e) {
                        throw getConversionError(targetType, inputValue);
                    }
                }
                case TypeTags.FLOAT_TAG -> {
                    try {
                        return Double.parseDouble(inputValue);
                    } catch (NumberFormatException e) {
                        throw getConversionError(targetType, inputValue);
                    }
                }
                case TypeTags.BOOLEAN_TAG -> {
                    char ch = inputValue.charAt(0);
                    if (ch == 't' && StreamStateMachine.TRUE.equals(inputValue)) {
                        return Boolean.TRUE;
                    } else if (ch == 'f' && StreamStateMachine.FALSE.equals(inputValue)) {
                        return Boolean.FALSE;
                    } else {
                        throw getConversionError(targetType, inputValue);
                    }
                }
                case TypeTags.NULL_TAG -> {
                    if (inputValue.charAt(0) == 'n' && StreamStateMachine.NULL.equals(inputValue)) {
                        return null;
                    } else {
                        throw getConversionError(targetType, inputValue);
                    }
                }
                case TypeTags.BYTE_TAG -> {
                    try {
                        return Integer.parseInt(inputValue);
                    } catch (NumberFormatException e) {
                        throw getConversionError(targetType, inputValue);
                    }
                }
                case TypeTags.UNION_TAG, TypeTags.FINITE_TYPE_TAG -> {
                    Object jsonVal = getNonStringValueAsJson(inputValue);
                    return convert(jsonVal, targetType);
                }
                case TypeTags.JSON_TAG, TypeTags.ANYDATA_TAG -> {
                    return getNonStringValueAsJson(inputValue);
                }
                default ->
                    // case TypeTags.STRING_TAG cannot come inside this method, an error needs to be thrown.
                        throw getConversionError(targetType, inputValue);
            }
        }

        protected State finalizeObject() throws ParserException {
            Type targetType = this.targetTypes.get(this.targetTypes.size() - 1);
            switch (targetType.getTag()) {
                case TypeTags.UNION_TAG, TypeTags.TABLE_TAG, TypeTags.FINITE_TYPE_TAG:
                    if (this.nodesStackSizeWhenUnionStarts == this.nodesStack.size()) {
                        this.targetTypes.remove(this.targetTypes.size() - 1);
                        this.currentJsonNode = convert(this.currentJsonNode, targetType);
                    }
                    break;
                case TypeTags.JSON_TAG, TypeTags.ANYDATA_TAG:
                    if (this.nodesStackSizeWhenUnionStarts == this.nodesStack.size()) {
                        this.targetTypes.remove(this.targetTypes.size() - 1);
                    }
                    break;
                case TypeTags.MAP_TAG:
                    this.targetTypes.remove(this.targetTypes.size() - 1);
                    break;
                case TypeTags.RECORD_TYPE_TAG:
                    this.targetTypes.remove(this.targetTypes.size() - 1);
                    BRecordType recordType = (BRecordType) targetType;
                    BMap<BString, Object> constructedMap = (BMap<BString, Object>) this.currentJsonNode;
                    List<String> notProvidedFields = new ArrayList<>();
                    for (Map.Entry<String, Field> stringFieldEntry : recordType.getFields().entrySet()) {
                        String fieldName = stringFieldEntry.getKey();
                        BString bFieldName = StringUtils.fromString(fieldName);
                        if (constructedMap.containsKey(bFieldName)) {
                            continue;
                        }
                        long fieldFlags = stringFieldEntry.getValue().getFlags();
                        if (SymbolFlags.isFlagOn(fieldFlags, SymbolFlags.REQUIRED)) {
                            throw new ParserException("missing required field '" + fieldName + "' of type '"
                                                      + stringFieldEntry.getValue().getFieldType().toString() +
                                                      "' in record '" + targetType + "'");
                        } else if (!SymbolFlags.isFlagOn(fieldFlags, SymbolFlags.OPTIONAL)) {
                            notProvidedFields.add(fieldName);
                        }
                    }
                    BMap<BString, Object> recordValue = createRecordValueWithDefaultValues(recordType.getPackage(),
                            recordType.getName(), notProvidedFields);
                    for (Map.Entry<BString, Object> fieldEntry : constructedMap.entrySet()) {
                        recordValue.populateInitialValue(fieldEntry.getKey(), fieldEntry.getValue());
                    }
                    if (recordType.isReadOnly()) {
                        recordValue.freezeDirect();
                    }
                    this.currentJsonNode = recordValue;
                    break;
                case TypeTags.ARRAY_TAG:
                    this.targetTypes.remove(this.targetTypes.size() - 1);
                    int listIndex = this.listIndices.remove(this.listIndices.size() - 1);
                    ArrayType arrayType = (ArrayType) targetType;
                    int targetSize = arrayType.getSize();
                    if (arrayType.getState() == ArrayType.ArrayState.CLOSED && targetSize > listIndex &&
                        !arrayType.hasFillerValue()) {
                        throw new ParserException("missing required number of values for the '" + arrayType +
                                                  "' array which does not have a filler value");
                    }
                    break;
                case TypeTags.TUPLE_TAG:
                    this.targetTypes.remove(this.targetTypes.size() - 1);
                    int tupleListIndex = this.listIndices.remove(this.listIndices.size() - 1);
                    TupleType tupleType = (TupleType) targetType;
                    int targetTupleSize = tupleType.getTupleTypes().size();
                    if (targetTupleSize > tupleListIndex) {
                        throw new ParserException("missing required number of values for the '" + tupleType
                                                  + "' tuple");
                    }
                    break;
                default:
                    throw new ParserException("unsupported type: '" + targetType + "'");
            }

            if (this.nodesStack.isEmpty()) {
                return DOC_END_STATE;
            }
            Object parentNode = this.nodesStack.pop();

            Type parentTargetType = this.targetTypes.get(this.targetTypes.size() - 1);
            return switch (parentTargetType.getTag()) {
                case TypeTags.RECORD_TYPE_TAG, TypeTags.MAP_TAG -> {
                    ((MapValueImpl<BString, Object>) parentNode).putForcefully(
                            StringUtils.fromString(fieldNames.pop()), currentJsonNode);
                    this.currentJsonNode = parentNode;
                    yield FIELD_END_STATE;
                }
                case TypeTags.ARRAY_TAG -> {
                    int listIndex = this.listIndices.get(this.listIndices.size() - 1);
                    ((ArrayValueImpl) parentNode).addRefValue(listIndex, currentJsonNode);
                    this.listIndices.set(this.listIndices.size() - 1, listIndex + 1);
                    this.currentJsonNode = parentNode;
                    yield ARRAY_ELEMENT_END_STATE;
                }
                case TypeTags.TUPLE_TAG -> {
                    int tupleListIndex = this.listIndices.get(this.listIndices.size() - 1);
                    ((TupleValueImpl) parentNode).addRefValue(tupleListIndex, currentJsonNode);
                    this.listIndices.set(this.listIndices.size() - 1, tupleListIndex + 1);
                    this.currentJsonNode = parentNode;
                    yield ARRAY_ELEMENT_END_STATE;
                }
                case TypeTags.UNION_TAG, TypeTags.JSON_TAG, TypeTags.ANYDATA_TAG, TypeTags.TABLE_TAG,
                        TypeTags.FINITE_TYPE_TAG -> {
                    if (TypeUtils.getImpliedType(TypeChecker.getType(parentNode)).getTag() == TypeTags.MAP_TAG) {
                        ((MapValueImpl<BString, Object>) parentNode).putForcefully(
                                StringUtils.fromString(fieldNames.pop()), currentJsonNode);
                        this.currentJsonNode = parentNode;
                        yield FIELD_END_STATE;
                    }
                    ArrayValueImpl arrayValue = (ArrayValueImpl) parentNode;
                    arrayValue.addRefValueForcefully(arrayValue.size(), this.currentJsonNode);
                    this.currentJsonNode = parentNode;
                    yield ARRAY_ELEMENT_END_STATE;
                }
                default -> throw new ParserException("unsupported type: '" + parentTargetType + "'");
            };
        }

        protected State initNewObject() throws ParserException {
            if (this.currentJsonNode != null) {
                this.nodesStack.push(currentJsonNode);
                Type lastTargetType = this.targetTypes.get(this.targetTypes.size() - 1);
                switch (lastTargetType.getTag()) {
                    case TypeTags.ARRAY_TAG:
                        int listIndex = this.listIndices.get(this.listIndices.size() - 1);
                        ArrayType arrayType = (ArrayType) lastTargetType;
                        int targetSize = arrayType.getSize();
                        if (arrayType.getState() == ArrayType.ArrayState.CLOSED && targetSize <= listIndex) {
                            throw new ParserException("'" + arrayType + "' " + ARRAY_SIZE_MISMATCH);
                        }
                        Type elementType = TypeUtils.getImpliedType(arrayType.getElementType());
                        this.addTargetType(elementType);
                        break;
                    case TypeTags.TUPLE_TAG:
                        int tupleListIndex = this.listIndices.get(this.listIndices.size() - 1);
                        TupleType tupleType = (TupleType) lastTargetType;
                        List<Type> tupleTypes = tupleType.getTupleTypes();
                        int targetTupleSize = tupleTypes.size();
                        Type tupleRestType = tupleType.getRestType();
                        boolean noRestType = tupleRestType == null;
                        Type tupleElementType;
                        if (targetTupleSize <= tupleListIndex) {
                            if (noRestType) {
                                throw new ParserException("'" + tupleType + "' tuple " + TUPLE_SIZE_MISMATCH);
                            } else {
                                tupleElementType = TypeUtils.getImpliedType(tupleRestType);
                            }
                        } else {
                            tupleElementType = TypeUtils.getImpliedType(tupleTypes.get(tupleListIndex));
                        }
                        this.addTargetType(tupleElementType);
                        break;
                    case TypeTags.MAP_TAG:
                        this.addTargetType(((MapType) lastTargetType).getConstrainedType());
                        break;
                    case TypeTags.RECORD_TYPE_TAG:
                        BRecordType recordType = (BRecordType) lastTargetType;
                        String fieldName = this.fieldNames.getFirst();
                        Map<String, Field> fields = recordType.getFields();
                        Field field = fields.get(fieldName);
                        if (field == null) {
                            this.addTargetType(recordType.restFieldType);
                        } else {
                            this.addTargetType(field.getFieldType());
                        }
                        break;
                    case TypeTags.UNION_TAG, TypeTags.JSON_TAG, TypeTags.ANYDATA_TAG, TypeTags.TABLE_TAG,
                            TypeTags.FINITE_TYPE_TAG:
                        break;
                    default:
                        throw new ParserException(UNSUPPORTED_TYPE + lastTargetType + "'");
                }
            }
            Type targetType = this.targetTypes.get(this.targetTypes.size() - 1);
            int targetTypeTag = targetType.getTag();
            switch (targetTypeTag) {
                case TypeTags.MAP_TAG, TypeTags.RECORD_TYPE_TAG ->
                        this.currentJsonNode = new MapValueImpl<>(targetType);
                case TypeTags.UNION_TAG, TypeTags.JSON_TAG, TypeTags.ANYDATA_TAG, TypeTags.TABLE_TAG,
                        TypeTags.FINITE_TYPE_TAG -> {
                    if (targetType.isReadOnly() && (targetTypeTag == TypeTags.JSON_TAG ||
                                                    targetTypeTag == TypeTags.ANYDATA_TAG)) {
                        this.currentJsonNode = new MapValueImpl<>(
                                new BMapType(PredefinedTypes.TYPE_READONLY_JSON, true));
                    } else {
                        this.currentJsonNode = new MapValueImpl<>(new BMapType(PredefinedTypes.TYPE_JSON));
                    }
                    if (this.nodesStackSizeWhenUnionStarts == -1) {
                        this.nodesStackSizeWhenUnionStarts = this.nodesStack.size();
                    }
                }
                default -> throw new ParserException(UNSUPPORTED_TYPE + targetType + "'");
            }
            return FIRST_FIELD_READY_STATE;
        }

        protected State initNewArray() throws ParserException {
            if (this.currentJsonNode != null) {
                this.nodesStack.push(currentJsonNode);
                Type lastTargetType = this.targetTypes.get(this.targetTypes.size() - 1);
                switch (lastTargetType.getTag()) {
                    case TypeTags.ARRAY_TAG:
                        int listIndex = this.listIndices.get(this.listIndices.size() - 1);
                        ArrayType arrayType = (ArrayType) lastTargetType;
                        int targetSize = arrayType.getSize();
                        if (arrayType.getState() == ArrayType.ArrayState.CLOSED && targetSize <= listIndex) {
                            throw new ParserException("'" + arrayType + "' " + ARRAY_SIZE_MISMATCH);
                        }
                        Type elementType = TypeUtils.getImpliedType(arrayType.getElementType());
                        this.addTargetType(elementType);
                        break;
                    case TypeTags.TUPLE_TAG:
                        int tupleListIndex = this.listIndices.get(this.listIndices.size() - 1);
                        TupleType tupleType = (TupleType) lastTargetType;
                        List<Type> tupleTypes = tupleType.getTupleTypes();
                        int targetTupleSize = tupleTypes.size();
                        Type tupleRestType = tupleType.getRestType();
                        boolean noRestType = tupleRestType == null;
                        Type tupleElementType;
                        if (targetTupleSize <= tupleListIndex) {
                            if (noRestType) {
                                throw new ParserException("'" + tupleType + "' " + TUPLE_SIZE_MISMATCH);
                            } else {
                                tupleElementType = TypeUtils.getImpliedType(tupleRestType);
                            }
                        } else {
                            tupleElementType = TypeUtils.getImpliedType(tupleTypes.get(tupleListIndex));
                        }
                        this.addTargetType(tupleElementType);
                        break;
                    case TypeTags.MAP_TAG:
                        this.addTargetType(((MapType) lastTargetType).getConstrainedType());
                        break;
                    case TypeTags.RECORD_TYPE_TAG:
                        BRecordType recordType = (BRecordType) lastTargetType;
                        String fieldName = this.fieldNames.getFirst();
                        Map<String, Field> fields = recordType.getFields();
                        Field field = fields.get(fieldName);
                        if (field == null) {
                            this.addTargetType(recordType.restFieldType);
                        } else {
                            this.addTargetType(field.getFieldType());
                        }
                        break;
                    case TypeTags.UNION_TAG, TypeTags.JSON_TAG, TypeTags.ANYDATA_TAG, TypeTags.TABLE_TAG,
                            TypeTags.FINITE_TYPE_TAG:
                        break;
                    default:
                        throw new ParserException(UNSUPPORTED_TYPE + lastTargetType + "'");
                }
            }
            Type targetType = this.targetTypes.get(this.targetTypes.size() - 1);
            int targetTypeTag = targetType.getTag();
            switch (targetTypeTag) {
                case TypeTags.ARRAY_TAG:
                    this.currentJsonNode = new ArrayValueImpl((ArrayType) targetType);
                    this.listIndices.add(0);
                    break;
                case TypeTags.TUPLE_TAG:
                    this.currentJsonNode = new TupleValueImpl((TupleType) targetType);
                    this.listIndices.add(0);
                    break;
                case TypeTags.UNION_TAG, TypeTags.JSON_TAG, TypeTags.ANYDATA_TAG, TypeTags.TABLE_TAG,
                        TypeTags.FINITE_TYPE_TAG:
                    if (targetType.isReadOnly() && (targetTypeTag == TypeTags.JSON_TAG ||
                                                    targetTypeTag == TypeTags.ANYDATA_TAG)) {
                        this.currentJsonNode = new ArrayValueImpl(new BArrayType(
                                PredefinedTypes.TYPE_READONLY_JSON, true));
                    } else {
                        this.currentJsonNode = new ArrayValueImpl(new BArrayType(PredefinedTypes.TYPE_JSON));
                    }
                    if (this.nodesStackSizeWhenUnionStarts == -1) {
                        this.nodesStackSizeWhenUnionStarts = this.nodesStack.size();
                    }
                    break;
                default:
                    throw new ParserException("target type is not array type");

            }
            return FIRST_ARRAY_ELEMENT_READY_STATE;
        }

        /**
         * Represents the state during a field name.
         */
        protected static class FieldNameState implements State {

            @Override
            public State transition(StateMachine sm, char[] buff, int i, int count) throws ParserException {
                char ch;
                State state = null;
                StreamStateMachine ssm = (StreamStateMachine) sm;
                for (; i < count; i++) {
                    ch = buff[i];
                    sm.processLocation(ch);
                    if (ch == sm.currentQuoteChar) {
                        sm.processFieldName();
                        Type parentTargetType = ssm.targetTypes.get(ssm.targetTypes.size() - 1);
                        // maps can have any field name
                        // for unions, json, anydata also we do nothing
                        if (parentTargetType.getTag() == TypeTags.RECORD_TYPE_TAG) {
                            BRecordType recordType = (BRecordType) parentTargetType;
                            String fieldName = sm.fieldNames.getFirst();
                            Map<String, Field> fields = recordType.getFields();
                            Field field = fields.get(fieldName);
                            if (field == null && recordType.sealed) {
                                throw new ParserException("field '" + fieldName + "' cannot be added to" +
                                                          " the closed record '" + recordType + "'");
                            }
                        }
                        state = END_FIELD_NAME_STATE;
                    } else if (ch == REV_SOL) {
                        state = FIELD_NAME_ESC_CHAR_PROCESSING_STATE;
                    } else if (ch == EOF) {
                        throw new ParserException(UNEXPECTED_END_OF_THE_INPUT_STREAM);
                    } else {
                        sm.append(ch);
                        state = this;
                        continue;
                    }
                    break;
                }
                sm.index = i + 1;
                return state;
            }

        }

        /**
         * Represents the state during a string field value is defined.
         */
        protected static class StringFieldValueState implements State {

            @Override
            public State transition(StateMachine sm, char[] buff, int i, int count) throws ParserException {
                State state = null;
                char ch;
                StreamStateMachine ssm = (StreamStateMachine) sm;
                for (; i < count; i++) {
                    ch = buff[i];
                    sm.processLocation(ch);
                    if (ch == sm.currentQuoteChar) {
                        Type targetType = ssm.targetTypes.get(ssm.targetTypes.size() - 1);
                        Object bString = StringUtils.fromString(sm.value());
                        switch (targetType.getTag()) {
                            case TypeTags.MAP_TAG:
                                try {
                                    bString = ValueConverter.getConvertedStringValue((BString) bString,
                                            ((MapType) targetType).getConstrainedType());
                                } catch (BError e) {
                                    throw new ParserException(e.getMessage());
                                }
                                break;
                            case TypeTags.RECORD_TYPE_TAG:
                                // in records, when processing the field name, target type is added to targetTypes list.
                                Type fieldType = getFieldType(sm, (BRecordType) targetType);
                                try {
                                    bString = ValueConverter.getConvertedStringValue((BString) bString, fieldType);
                                } catch (BError e) {
                                    throw new ParserException(e.getMessage());
                                }
                                break;
                            case TypeTags.JSON_TAG, TypeTags.ANYDATA_TAG, TypeTags.UNION_TAG, TypeTags.TABLE_TAG,
                                    TypeTags.FINITE_TYPE_TAG:
                                break;
                            default:
                                throw new ParserException(UNSUPPORTED_TYPE + targetType + "'");
                        }
                        ((MapValueImpl<BString, Object>) sm.currentJsonNode).putForcefully(
                                StringUtils.fromString(sm.fieldNames.pop()), bString);
                        state = FIELD_END_STATE;
                    } else if (ch == REV_SOL) {
                        state = STRING_FIELD_ESC_CHAR_PROCESSING_STATE;
                    } else if (ch == EOF) {
                        throw new ParserException(UNEXPECTED_END_OF_THE_INPUT_STREAM);
                    } else {
                        sm.append(ch);
                        state = this;
                        continue;
                    }
                    break;
                }
                sm.index = i + 1;
                return state;
            }

            private static Type getFieldType(StateMachine sm, BRecordType targetType) {
                String fieldName = sm.fieldNames.getFirst();
                Map<String, Field> fields = targetType.getFields();
                Field field = fields.get(fieldName);
                return field == null ? targetType.restFieldType : field.getFieldType();
            }

        }

        /**
         * Represents the state during a string array element is defined.
         */
        protected static class StringArrayElementState implements State {

            @Override
            public State transition(StateMachine sm, char[] buff, int i, int count) throws ParserException {
                State state = null;
                char ch;
                for (; i < count; i++) {
                    ch = buff[i];
                    sm.processLocation(ch);
                    StreamStateMachine ssm = (StreamStateMachine) sm;
                    if (ch == sm.currentQuoteChar) {
                        Type targetType = ssm.targetTypes.get(ssm.targetTypes.size() - 1);
                        BString bString = StringUtils.fromString(sm.value());
                        int listIndex = ssm.listIndices.get(ssm.listIndices.size() - 1);
                        switch (targetType.getTag()) {
                            case TypeTags.ARRAY_TAG:
                                try {
                                    ((ArrayValueImpl) sm.currentJsonNode)
                                            .convertStringAndAddRefValue(listIndex, bString);
                                } catch (BError e) {
                                    throw new ParserException(e.getMessage());
                                }
                                ssm.listIndices.set(ssm.listIndices.size() - 1, listIndex + 1);
                                break;
                            case TypeTags.TUPLE_TAG:
                                listIndex = ssm.listIndices.get(ssm.listIndices.size() - 1);
                                try {
                                    ((TupleValueImpl) sm.currentJsonNode)
                                            .convertStringAndAddRefValue(listIndex, bString);
                                } catch (BError e) {
                                    throw new ParserException(e.getMessage());
                                }
                                ssm.listIndices.set(ssm.listIndices.size() - 1, listIndex + 1);
                                break;
                            case TypeTags.JSON_TAG, TypeTags.ANYDATA_TAG, TypeTags.UNION_TAG, TypeTags.FINITE_TYPE_TAG:
                                ArrayValueImpl arrayValue = (ArrayValueImpl) sm.currentJsonNode;
                                arrayValue.addRefValueForcefully(arrayValue.size(), bString);
                                break;
                            default:
                                throw new ParserException(UNSUPPORTED_TYPE + targetType + "'");
                        }
                        state = ARRAY_ELEMENT_END_STATE;
                    } else if (ch == REV_SOL) {
                        state = STRING_AE_ESC_CHAR_PROCESSING_STATE;
                    } else if (ch == EOF) {
                        throw new ParserException(UNEXPECTED_END_OF_THE_INPUT_STREAM);
                    } else {
                        sm.append(ch);
                        state = this;
                        continue;
                    }
                    break;
                }
                sm.index = i + 1;
                return state;
            }

        }

        /**
         * Represents the state during a string value is defined.
         */
        protected static class StringValueState implements State {

            @Override
            public State transition(StateMachine sm, char[] buff, int i, int count) throws ParserException {
                State state = null;
                char ch;
                for (; i < count; i++) {
                    ch = buff[i];
                    sm.processLocation(ch);
                    StreamStateMachine ssm = (StreamStateMachine) sm;
                    if (ch == sm.currentQuoteChar) {
                        Type targetType = ssm.targetTypes.get(ssm.targetTypes.size() - 1);
                        BString bString = StringUtils.fromString(sm.value());
                        if (ssm.nodesStackSizeWhenUnionStarts == -1) {
                            try {
                                sm.currentJsonNode = ValueConverter.getConvertedStringValue(bString, targetType);
                            } catch (BError e) {
                                throw new ParserException(e.getMessage());
                            }
                        } else {
                            sm.currentJsonNode = bString;
                        }
                        state = DOC_END_STATE;
                    } else if (ch == REV_SOL) {
                        state = STRING_VAL_ESC_CHAR_PROCESSING_STATE;
                    } else if (ch == EOF) {
                        throw new ParserException(UNEXPECTED_END_OF_THE_INPUT_STREAM);
                    } else {
                        sm.append(ch);
                        state = this;
                        continue;
                    }
                    break;
                }
                sm.index = i + 1;
                return state;
            }

        }

        private static Object getNonStringValueAsJson(String str) throws ParserException {
            if (str.indexOf('.') >= 0) {
                try {
                    if (isNegativeZero(str) || mode == FROM_JSON_FLOAT_STRING) {
                        return Double.parseDouble(str);
                    } else {
                        return new DecimalValue(str);
                    }
                } catch (NumberFormatException ignore) {
                    throw new ParserException(UNRECOGNIZED_TOKEN + str + "'");
                }
            } else {
                char ch = str.charAt(0);
                if (ch == 't' && TRUE.equals(str)) {
                    return Boolean.TRUE;
                } else if (ch == 'f' && FALSE.equals(str)) {
                    return Boolean.FALSE;
                } else if (ch == 'n' && NULL.equals(str)) {
                    return null;
                } else {
                    try {
                        if (isNegativeZero(str) || mode == FROM_JSON_FLOAT_STRING) {
                            return Double.parseDouble(str);
                        } else if (isExponential(str) || mode == FROM_JSON_DECIMAL_STRING) {
                            return new DecimalValue(str);
                        } else {
                            return Long.parseLong(str);
                        }
                    } catch (NumberFormatException ignore) {
                        throw new ParserException(UNRECOGNIZED_TOKEN + str + "'");
                    }
                }
            }
        }

        private void processNonStringValueAsJson(String str, ValueType type) throws ParserException {
            setValueToJsonType(type, getNonStringValueAsJson(str));
        }

        void processNonStringValue(ValueType type) throws ParserException {
            String str = value();
            Type targetType = this.targetTypes.get(this.targetTypes.size() - 1);
            Type referredType = TypeUtils.getImpliedType(targetType);
            switch (referredType.getTag()) {
                case TypeTags.UNION_TAG, TypeTags.FINITE_TYPE_TAG:
                    processNonStringValueAsJson(str, type);
                    if (this.nodesStackSizeWhenUnionStarts == -1) {
                        this.currentJsonNode = convert(this.currentJsonNode, targetType);
                    }
                    break;
                case TypeTags.ANYDATA_TAG, TypeTags.JSON_TAG, TypeTags.TABLE_TAG:
                    processNonStringValueAsJson(str, type);
                    break;
                case TypeTags.ARRAY_TAG:
                    if (this.currentJsonNode == null) {
                        throw new ParserException(UNRECOGNIZED_TOKEN + str + "'");
                    }
                    int listIndex = this.listIndices.get(this.listIndices.size() - 1);
                    ArrayType arrayType = (ArrayType) referredType;
                    Type elementType = TypeUtils.getImpliedType(arrayType.getElementType());
                    ((ArrayValueImpl) this.currentJsonNode).addRefValue(listIndex, convertValues(elementType, str));
                    this.listIndices.set(this.listIndices.size() - 1, listIndex + 1);
                    break;
                case TypeTags.TUPLE_TAG:
                    if (this.currentJsonNode == null) {
                        throw new ParserException(UNRECOGNIZED_TOKEN + str + "'");
                    }
                    int tupleListIndex = this.listIndices.get(this.listIndices.size() - 1);
                    TupleType tupleType = (TupleType) referredType;
                    List<Type> tupleTypes = tupleType.getTupleTypes();
                    int targetTupleSize = tupleTypes.size();
                    Type tupleRestType = tupleType.getRestType();
                    boolean noRestType = tupleRestType == null;
                    Type tupleElementType;
                    if (targetTupleSize <= tupleListIndex) {
                        if (noRestType) {
                            throw new ParserException("'" + tupleType + "' " + TUPLE_SIZE_MISMATCH);
                        } else {
                            tupleElementType = TypeUtils.getImpliedType(tupleRestType);
                        }
                    } else {
                        tupleElementType = TypeUtils.getImpliedType(tupleTypes.get(tupleListIndex));
                    }
                    ((TupleValueImpl) this.currentJsonNode).addRefValueForcefully(tupleListIndex,
                            convertValues(tupleElementType, str));
                    this.listIndices.set(this.listIndices.size() - 1, tupleListIndex + 1);
                    break;
                case TypeTags.MAP_TAG:
                    if (this.currentJsonNode == null) {
                        throw new ParserException(UNRECOGNIZED_TOKEN + str + "'");
                    }
                    MapType mapType = (MapType) referredType;
                    Type constrainedType = TypeUtils.getImpliedType(mapType.getConstrainedType());
                    ((MapValueImpl<BString, Object>) this.currentJsonNode).putForcefully(
                            StringUtils.fromString(this.fieldNames.pop()), convertValues(constrainedType, str));
                    break;
                case TypeTags.RECORD_TYPE_TAG:
                    if (this.currentJsonNode == null) {
                        throw new ParserException(UNRECOGNIZED_TOKEN + str + "'");
                    }
                    BRecordType recordType = (BRecordType) referredType;
                    String fieldName = this.fieldNames.pop();
                    Map<String, Field> fields = recordType.getFields();
                    Field field = fields.get(fieldName);
                    Type fieldType = field == null ? recordType.restFieldType : field.getFieldType();
                    ((MapValueImpl<BString, Object>) this.currentJsonNode).putForcefully(
                            StringUtils.fromString(fieldName), convertValues(TypeUtils.getImpliedType(fieldType), str));
                    break;
                default:
                    this.currentJsonNode = convertValues(referredType, str);
                    break;
            }
        }

        void setValueToJsonType(ValueType type, Object value) {
            switch (type) {
                case ARRAY_ELEMENT:
                    ArrayValueImpl arrayValue = (ArrayValueImpl) this.currentJsonNode;
                    arrayValue.addRefValueForcefully(arrayValue.size(), value);
                    break;
                case FIELD:
                    ((MapValueImpl<BString, Object>) this.currentJsonNode).putForcefully(
                            StringUtils.fromString(this.fieldNames.pop()), value);
                    break;
                default:
                    this.currentJsonNode = value;
                    break;
            }
        }

        private static Object convert(Object value, Type targetType) {
            return convert(value, targetType, new HashSet<>());
        }

        private static Object convert(Object value, Type targetType, Set<TypeValuePair> unresolvedValues) {

            if (value == null) {
                if (TypeUtils.getImpliedType(targetType).isNilable()) {
                    return null;
                }
                throw createError(ErrorReasons.BALLERINA_PREFIXED_CONVERSION_ERROR,
                        ErrorHelper.getErrorDetails(ErrorCodes.CANNOT_CONVERT_NIL, targetType));
            }

            Type sourceType = TypeUtils.getImpliedType(TypeChecker.getType(value));

            TypeValuePair typeValuePair = new TypeValuePair(value, targetType);
            if (unresolvedValues.contains(typeValuePair)) {
                throw createError(ErrorReasons.BALLERINA_PREFIXED_CYCLIC_VALUE_REFERENCE_ERROR,
                        ErrorHelper.getErrorMessage(ErrorCodes.CYCLIC_VALUE_REFERENCE, sourceType));
            }
            unresolvedValues.add(typeValuePair);

            List<String> errors = new ArrayList<>();
            Type convertibleType = TypeConverter.getConvertibleType(value, targetType, null,
                    new HashSet<>(), errors, true);
            if (convertibleType == null) {
                throw CloneUtils.createConversionError(value, targetType, errors);
            }

            Object newValue;
            Type matchingType = TypeUtils.getImpliedType(convertibleType);
            switch (sourceType.getTag()) {
                case TypeTags.MAP_TAG:
                    newValue = convertMap((MapValueImpl<BString, Object>) value, matchingType, convertibleType,
                            unresolvedValues);
                    break;
                case TypeTags.ARRAY_TAG:
                    newValue = convertArray((ArrayValueImpl) value, matchingType, convertibleType, unresolvedValues);
                    break;
                case TypeTags.TABLE_TAG, TypeTags.RECORD_TYPE_TAG, TypeTags.TUPLE_TAG:
                    // source type can't be tuple, record and table in the stream parser
                    throw createConversionError(value, targetType);
                default:
                    if (TypeChecker.isRegExpType(targetType) && matchingType.getTag() == TypeTags.STRING_TAG) {
                        try {
                            newValue = RegExpFactory.parse(((BString) value).getValue());
                            break;
                        } catch (BError e) {
                            throw createConversionError(value, targetType, e.getMessage());
                        }
                    }

                    if (TypeTags.isXMLTypeTag(matchingType.getTag())) {
                        String xmlString = value.toString();
                        try {
                            newValue = BalStringUtils.parseXmlExpressionStringValue(xmlString);
                        } catch (BError e) {
                            throw createConversionError(value, targetType);
                        }
                        if (matchingType.isReadOnly()) {
                            ((BRefValue) newValue).freezeDirect();
                        }
                        break;
                    }

                    // can't put the below, above handling xml because for selectively mutable types when the
                    // provided value is readonly, if the target type is non readonly, checkIsType provides true, but
                    // we can't just clone the value as it should be non readonly.
                    if (TypeChecker.checkIsType(value, matchingType)) {
                        newValue = value;
                        break;
                    }

                    // handle primitive values
                    if (sourceType.getTag() <= TypeTags.BOOLEAN_TAG) {
                        // has to be a numeric conversion.
                        newValue = TypeConverter.convertValues(matchingType, value);
                        break;
                    }
                    // should never reach here
                    throw createConversionError(value, targetType);
            }

            unresolvedValues.remove(typeValuePair);
            return newValue;
        }


        private static Object convertArray(ArrayValueImpl array, Type targetType, Type targetRefType,
                                           Set<TypeValuePair> unresolvedValues) {
            switch (targetType.getTag()) {
                case TypeTags.ARRAY_TAG:
                    ArrayType arrayType = (ArrayType) targetType;
                    ArrayValueImpl newArray = new ArrayValueImpl(targetRefType, arrayType.getSize());
                    for (int i = 0; i < array.size(); i++) {
                        newArray.addRefValueForcefully(i, convert(array.getRefValue(i), arrayType.getElementType(),
                                unresolvedValues));
                    }
                    return newArray;
                case TypeTags.TUPLE_TAG:
                    TupleType tupleType = (TupleType) targetType;
                    int minLen = tupleType.getTupleTypes().size();
                    BListInitialValueEntry[] tupleValues = new BListInitialValueEntry[array.size()];
                    for (int i = 0; i < array.size(); i++) {
                        Type elementType = (i < minLen) ? tupleType.getTupleTypes().get(i) : tupleType.getRestType();
                        tupleValues[i] = ValueCreator.createListInitialValueEntry(convert(array.getRefValue(i),
                                elementType, unresolvedValues));
                    }
                    return new TupleValueImpl(targetRefType, tupleValues);
                case TypeTags.TABLE_TAG:
                    TableType tableType = (TableType) targetType;
                    for (int i = 0; i < array.size(); i++) {
                        Object bMap = convert(array.get(i), tableType.getConstrainedType(), unresolvedValues);
                        array.setRefValueForcefully(i, bMap);
                    }
                    array.setArrayRefTypeForcefully(TypeCreator.createArrayType(tableType.getConstrainedType()),
                            array.size());
                    BArray fieldNames = StringUtils.fromStringArray(tableType.getFieldNames());
                    return new TableValueImpl<>(targetRefType, array, (ArrayValue) fieldNames);
                default:
                    break;
            }
            // should never reach here
            throw createConversionError(array, targetType);
        }

        private static Object convertMap(MapValueImpl<BString, Object> map, Type targetType, Type targetRefType,
                                         Set<TypeValuePair> unresolvedValues) {
            Set<Map.Entry<BString, Object>> mapEntrySet = map.entrySet();
            switch (targetType.getTag()) {
                case TypeTags.MAP_TAG:
                    Type constraintType = ((MapType) targetType).getConstrainedType();
                    for (Map.Entry<BString, Object> entry : mapEntrySet) {
                        Object newValue = convert(entry.getValue(), constraintType, unresolvedValues);
                        map.putForcefully(entry.getKey(), newValue);
                    }
                    map.setTypeForcefully(targetRefType);
                    return map;
                case TypeTags.RECORD_TYPE_TAG:
                    RecordType recordType = (RecordType) targetType;
                    Type restFieldType = recordType.getRestFieldType();
                    Map<String, Type> targetTypeField = new HashMap<>();
                    for (Field field : recordType.getFields().values()) {
                        targetTypeField.put(field.getFieldName(), field.getFieldType());
                    }
                    for (Map.Entry<BString, Object> entry : mapEntrySet) {
                        Type fieldType = targetTypeField.getOrDefault(entry.getKey().toString(), restFieldType);
                        Object newValue = convert(entry.getValue(), fieldType, unresolvedValues);
                        map.putForcefully(entry.getKey(), newValue);
                    }
                    Optional<IntersectionType> intersectionType =
                            ((BRecordType) TypeUtils.getImpliedType(targetRefType)).getIntersectionType();
                    if (targetRefType.isReadOnly() && intersectionType.isPresent() && !map.getType().isReadOnly()) {
                        Type mutableType = ReadOnlyUtils.getMutableType((BIntersectionType) intersectionType.get());
                        MapValueImpl<BString, Object> bMapValue = (MapValueImpl<BString, Object>)
                                ValueUtils.createRecordValue(mutableType.getPackage(), mutableType.getName(), map);
                        bMapValue.freezeDirect();
                        return bMapValue;
                    }
                    return ValueUtils.createRecordValue(targetRefType.getPackage(), targetRefType.getName(), map);
                default:
                    break;
            }
            // should never reach here
            throw createConversionError(map, targetType);
        }

    }



}

