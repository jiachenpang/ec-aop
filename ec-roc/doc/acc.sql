
create table N3_ACC_DIFF
(
  batchno      VARCHAR2(100) not null,
  los          INTEGER not null,
  ors          INTEGER not null,
  lrs          INTEGER not null,
  bal          INTEGER not null,
  totalline    INTEGER not null,
  starttime    DATE not null,
  endtime      DATE not null,
  costtime     INTEGER not null,
  accountday   DATE not null,
  accounttype  VARCHAR2(32) not null,
  provincecode CHAR(2)
);

comment on column N3_ACC_DIFF.batchno
  is '对账批次号';
comment on column N3_ACC_DIFF.los
  is '左边有数据而右边没有数据的记录数';
comment on column N3_ACC_DIFF.ors
  is '左边没有数据而右边有数据的记录数';
comment on column N3_ACC_DIFF.lrs
  is '左右两边都有数据，但是对账不平的记录数';
comment on column N3_ACC_DIFF.bal
  is '左右两边都有数据，并且对账平的记录数';
comment on column N3_ACC_DIFF.totalline
  is '以上LO,OR,LR,BAL的总和';
comment on column N3_ACC_DIFF.starttime
  is '对账开始时间';
comment on column N3_ACC_DIFF.endtime
  is '对账结束时间';
comment on column N3_ACC_DIFF.costtime
  is '对账总耗时秒数';
comment on column N3_ACC_DIFF.accountday
  is '对账日期';
comment on column N3_ACC_DIFF.accounttype
  is '对账类型';
comment on column N3_ACC_DIFF.provincecode
  is '省份编码';
-- Create/Recreate primary, unique and foreign key constraints
alter table N3_ACC_DIFF
  add constraint PK_N3_ACC_DIFF primary key (BATCHNO);

create table N3_ACC_DIFF_DETAIL
(
  batchno     VARCHAR2(100) not null,
  difftype    VARCHAR2(2) not null,
  leftdata    VARCHAR2(2000),
  rightdata   VARCHAR2(2000),
  diff        VARCHAR2(1000),
  leftkey     VARCHAR2(100),
  rightkey    VARCHAR2(100),
  subdifftype VARCHAR2(2)
);

comment on column N3_ACC_DIFF_DETAIL.batchno
  is '对账批次号';
comment on column N3_ACC_DIFF_DETAIL.difftype
  is '差别类型(L0,0R,LR)';
comment on column N3_ACC_DIFF_DETAIL.leftdata
  is '左边数据类型(json串)';
comment on column N3_ACC_DIFF_DETAIL.rightdata
  is '右边数据类型(json串)';
comment on column N3_ACC_DIFF_DETAIL.diff
  is '具体的差异描述（字段名对  : 字段值对）';
comment on column N3_ACC_DIFF_DETAIL.leftkey
  is '左边数据的主键';
comment on column N3_ACC_DIFF_DETAIL.rightkey
  is '右边数据的主键';
comment on column N3_ACC_DIFF_DETAIL.subdifftype
  is '具体的差别类型';
-- Create/Recreate indexes 
create index IDX_N3_ACC_DETAIL_BATCHNO on N3_ACC_DIFF_DETAIL (BATCHNO);
