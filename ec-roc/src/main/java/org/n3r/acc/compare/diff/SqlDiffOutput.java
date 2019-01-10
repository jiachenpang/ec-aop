package org.n3r.acc.compare.diff;

import org.apache.commons.lang3.StringUtils;
import static org.n3r.acc.compare.CompareContext.*;
import org.n3r.acc.compare.Record;
import org.n3r.acc.compare.impl.DiffStat;
import org.n3r.acc.utils.AccUtils;
import org.n3r.config.Configable;
import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.tag.FromSpecConfig;
import org.n3r.esql.Esql;

import java.util.Map;

@EcRocTag("SqlDiff")
public class SqlDiffOutput implements DiffOutput, FromSpecConfig<SqlDiffOutput> {
    private String batchNo;
    private Esql esqlData;
    private Esql esqlStat;
    private OnlyLeftDiffListener onlyLeftDiffListener;
    private Map<String, String> context;

    @Override
    public void onlyRight(Record record) {
        logDiff(DiffMode.OnlyRight, null, record, null, null);
    }

    private void logDiff(DiffMode diffMode, Record lRecord, Record rRecord,
                         String diffs, String subDiffType) {
        switch (diffMode) {
            case OnlyRight:
                esqlData.params(batchNo, "0R", null, rRecord.toString(),
                        null, null, rRecord.getKey(), null).execute();
                break;
            case OnlyLeft:
                esqlData.params(batchNo, "L0", lRecord.toString(), null, null, lRecord.getKey()
                        , null, null).execute();
                break;
            case Diff:
                esqlData.params(batchNo, "LR", lRecord.toString(), rRecord.toString(), diffs,
                        lRecord.getKey(), rRecord.getKey(), subDiffType).execute();
            case Balance:
                break;
        }
    }

    @Override
    public Record onlyLeft(Record record) {
        Record rightRecord = null;
        if (onlyLeftDiffListener != null) {
            rightRecord = onlyLeftDiffListener.onLeftRight(record);
        }

        if (rightRecord == null) logDiff(DiffMode.OnlyLeft, record, null, null, null);

        return rightRecord;
    }

    @Override
    public void diff(Record leftRecord, Record rightRecord, String diffs, String subDiffType) {
        logDiff(DiffMode.Diff, leftRecord, rightRecord, diffs, subDiffType);
    }

    @Override
    public void balance(Record leftRecord, Record rightRecord) {
        logDiff(DiffMode.Balance, leftRecord, rightRecord, null, null);
    }


    @Override
    public void startCompare(String batchNo) {
        this.batchNo = batchNo;
    }

    @Override
    public void endCompare(DiffStat diffStat) {
        esqlStat.params(batchNo, diffStat.getOnlyLefts()
                , diffStat.getOnlyRights()
                , diffStat.getDiffs()
                , diffStat.getBalances()
                , diffStat.getStartTime()
                , diffStat.getEndTime(), diffStat.getTotal()
                , diffStat.getCostTime()
                , RDate.parse(context.get(ACCOUNT_DAY), "yyyyMMdd")
                , context.get(ACCOUNT_TYPE)
                ,context.get(PROVINCE_CODE)).execute();

    }


    @Override
    public SqlDiffOutput fromSpec(Configable config, Map<String, String> context) {
        String connectionName = config.getStr("diff.connectionName", Esql.DEFAULT_CONN_NAME);
        String sqlid = config.getStr("diff.sqlid");
        String sqlidStat = config.getStr("diff.sqlidStat");
        String sqlfile = config.getStr("diff.sqlfile");
        esqlData = new Esql(connectionName).useSqlFile(sqlfile).id(sqlid);
        esqlStat = new Esql(connectionName).useSqlFile(sqlfile).id(sqlidStat);
        this.context = context;

        String diffListener = config.getStr("diff.listener");
        if (StringUtils.isNotEmpty(diffListener))
            this.onlyLeftDiffListener = AccUtils.parseSpec(diffListener, OnlyLeftDiffListener.class, config, context);

        return this;
    }
}
