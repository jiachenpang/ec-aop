package org.n3r.acc.compare.impl;

import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Splitter;
import com.jayway.jsonpath.internal.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.n3r.acc.compare.*;
import org.n3r.acc.compare.diff.DiffMode;
import org.n3r.acc.compare.diff.DiffOutput;
import org.n3r.acc.compare.left.LeftData;
import org.n3r.acc.compare.right.RightData;
import org.n3r.acc.utils.AccUtils;
import org.n3r.config.Configable;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.tag.FromSpecConfig;
import org.n3r.fastjson.EjsonEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.n3r.acc.utils.EqualsUtils.equalsTo;

@EcRocTag("Outside")
public class OutsideDataCompare extends DataCompare implements FromSpecConfig<OutsideDataCompare> {
    private List<CompareField> compareFields;
    private LeftData leftData;
    private RightData rightData;
    private CompareField compareKey;
    private DiffOutput diffOutput;
    private DiffStat diffStat = new DiffStat();
    private BatchNoCreator batchNoCreator;
    private CompareContext compareContext;

    @Override
    public void compare() {
        try {
            String batchNo = batchNoCreator.createBatchNo();
            rightData.start();
            leftData.start();
            diffStat.start(batchNo);

            diffOutput.startCompare(batchNo);
            compareRecords();
            diffStat.end();

            diffOutput.endCompare(diffStat);


        } finally {
            IOUtils.closeQuietly(leftData);
            IOUtils.closeQuietly(rightData);
        }
    }

    private void compareRecords() {
        Record rightRecord = null;
        while ((rightRecord = rightData.readRecord(compareKey.getRight())) != null) {
            String key = rightRecord.getKey();
            Record leftRecord = leftData.popup(key);

            if (leftRecord == null) {
                diffOutput.onlyRight(rightRecord);
                diffStat.stat(DiffMode.OnlyRight);
            } else compareRecord(leftRecord, rightRecord);
        }

        for (Record record : leftData.popupAll()) {
            rightRecord = diffOutput.onlyLeft(record);
            if (rightRecord == null) {
                diffStat.stat(DiffMode.OnlyLeft);
            } else {
                compareRecord(record, rightRecord);
            }
        }
    }

    private void compareRecord(Record leftRecord, Record rightRecord) {
        JSONObject diffs = new JSONObject();
        StringBuilder subDiffType = new StringBuilder(compareFields.size());
        for (CompareField compareField : compareFields) {
            Object leftFieldValue = leftRecord.getValue(compareField.getLeft());
            Object rightFieldValue = rightRecord.getValue(compareField.getRight());
            if (equalsTo(leftFieldValue, rightFieldValue)) {
                subDiffType.append('0');
            } else {
                diffs.put(createDiffKey(compareField), createDiff(leftFieldValue, rightFieldValue));
                subDiffType.append('1');
            }
        }

        if (diffs.size() <= 0) {
            diffOutput.balance(leftRecord, rightRecord);
            diffStat.stat(DiffMode.Balance);
        } else {
            diffOutput.diff(leftRecord, rightRecord,
                    new EjsonEncoder().bare().encode(diffs), subDiffType.toString());
            diffStat.stat(DiffMode.Diff);
        }
    }

    private String createDiff(Object leftFieldValue, Object rightFieldValue) {
        return leftFieldValue + "-" + rightFieldValue;
    }

    private String createDiffKey(CompareField compareField) {
        if (compareField.getLeft().getName().equals(compareField.getRight().getName()))
            return compareField.getLeft().getName();

        return createDiff(compareField.getLeft().getName(), compareField.getRight().getName());
    }


    @Override
    public OutsideDataCompare fromSpec(Configable config, Map<String, String> context) {
        parseBatchNoCreator(config, context);
        parseLeftData(config, context);
        parseRightData(config, context);
        pareCompareKey(config, context);
        parseCompareFields(config, context);
        parseDiffOutput(config, context);

        return this;
    }

    private void parseBatchNoCreator(Configable config, Map<String, String> context) {
        String batchNoCreatorConfig = config.getStr("batchNoCreator", "@currentTimeMillis");

        this.batchNoCreator = AccUtils.parseSpec(batchNoCreatorConfig, BatchNoCreator.class, config, context);
        if (this.batchNoCreator instanceof CompareContextAware)
            ((CompareContextAware) batchNoCreator).setCompareContext(context);
    }

    private void parseLeftData(Configable config, Map<String, String> context) {
        String leftData = config.getStr("leftData");
        if (StringUtils.isEmpty(leftData))
            throw new RuntimeException("leftData should be defined");

        this.leftData = AccUtils.parseSpec(leftData, LeftData.class, config, context);
    }

    private void parseDiffOutput(Configable config, Map<String, String> context) {
        String diffOutput = config.getStr("diffOutput");
        if (StringUtils.isEmpty(diffOutput))
            throw new RuntimeException("diffOutput should be defined");

        this.diffOutput = AccUtils.parseSpec(diffOutput, DiffOutput.class, config, context);
    }

    private void parseCompareFields(Configable config, Map<String, String> context) {
        String compareFields = config.getStr("compareFields");
        if (StringUtils.isEmpty(compareFields))
            throw new RuntimeException("compareFields should be defined");

        this.compareFields = parseCompareFields(compareFields);
    }

    private void pareCompareKey(Configable config, Map<String, String> context) {
        String compareKey = config.getStr("compareKey");
        if (StringUtils.isEmpty(compareKey))
            throw new RuntimeException("compareKey should be defined");

        this.compareKey = parseCompareKey(compareKey);
    }

    private void parseRightData(Configable config, Map<String, String> context) {
        String rightData = config.getStr("rightData");
        if (StringUtils.isEmpty(rightData))
            throw new RuntimeException("rightData should be defined");

        this.rightData = AccUtils.parseSpec(rightData, RightData.class, config, context);
    }

    private List<CompareField> parseCompareFields(String compareFields) {
        ArrayList compareKeys = new ArrayList<CompareField>();
        for (String str : Splitter.on(',').omitEmptyStrings().trimResults().split(compareFields))
            compareKeys.add(parseCompareKey(str));

        return compareKeys;
    }


    private CompareField parseCompareKey(String compareKey) {
        CompareField ck = new CompareField();
        int seperatorPos = compareKey.indexOf(':');
        if (seperatorPos < 0)
            throw new RuntimeException("compareKey is invalid, required {leftIndex or leftKey}:{rightIndex or rightKey}");

        String leftKey = compareKey.substring(0, seperatorPos);
        String rightKey = compareKey.substring(seperatorPos + 1);
        ck.createCompareKey(leftKey, rightKey);

        return ck;
    }

}
