package org.n3r.acc.compare.impl;

import org.n3r.acc.compare.diff.DiffMode;

import java.util.Date;

/**
 * Statistics for Difference.
 */
public class DiffStat {
    private long start;
    private int onlyRights;
    private int balances;
    private int diffs;
    private int onlyLefts;
    private long end;
    private String batchNo;
    private int totals;

    public void start(String batchNo) {
        this.start = System.currentTimeMillis();
        this.batchNo = batchNo;
    }

    public void stat(DiffMode diffMode) {
        ++totals;
        switch (diffMode) {
            case Balance:
                ++this.balances;
                break;
            case Diff:
                ++this.diffs;
                break;
            case OnlyLeft:
                ++this.onlyLefts;
                break;
            case OnlyRight:
                ++this.onlyRights;
                break;
        }
    }

    public void end() {
        this.end = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return "DiffStat{" +
                "start=" + start +
                ", onlyRights=" + onlyRights +
                ", balances=" + balances +
                ", diffs=" + diffs +
                ", onlyLefts=" + onlyLefts +
                ", end=" + end +
                ", batchNo='" + batchNo + '\'' +
                '}';
    }


    public int getOnlyRights() {
        return onlyRights;
    }

    public int getBalances() {
        return balances;
    }

    public int getDiffs() {
        return diffs;
    }

    public int getOnlyLefts() {
        return onlyLefts;
    }


    public int getTotal() {
        return totals;
    }

    public Date getStartTime() {
        return new Date(start);
    }

    public Date getEndTime() {
        return new Date(end);
    }

    public long getCostTime() {
        return (end - start)/1000;
    }

}
