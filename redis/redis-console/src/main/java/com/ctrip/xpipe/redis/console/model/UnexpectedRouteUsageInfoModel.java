package com.ctrip.xpipe.redis.console.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.*;

@JsonPropertyOrder({"unExpectedRouteUsedClusterNum", "unexpectedRouteUsageDirectionInfos", "unexpectedRouteUsageDetailInfos"})
public class UnexpectedRouteUsageInfoModel {

    private int unexpectedRouteUsedClusterNum;

    private Map<String, Integer> unexpectedRouteUsageDirectionInfos;

    private Map<String, List<UnexpectedRouteUsageInfo>> unexpectedRouteUsageDetailInfos;

    public UnexpectedRouteUsageInfoModel() {
        this.unexpectedRouteUsageDetailInfos = new HashMap<>();
        this.unexpectedRouteUsageDirectionInfos = new HashMap<>();
    }

    public void addUsedWrongRouteCluster(String clusterName, String srcDcName, String dstDcName, Set<Integer> usedRouteId, Integer chooseRouteId) {
        if(!unexpectedRouteUsageDetailInfos.containsKey(clusterName)) {
            unexpectedRouteUsedClusterNum++;
            this.unexpectedRouteUsageDetailInfos.put(clusterName, new ArrayList<>());
        }
        this.unexpectedRouteUsageDetailInfos.get(clusterName).add(new UnexpectedRouteUsageInfo(clusterName, srcDcName, dstDcName, usedRouteId, chooseRouteId))
;
        String direction = String.format("%s------>%s", srcDcName, dstDcName);
        if(!unexpectedRouteUsageDirectionInfos.containsKey(direction)) {
            this.unexpectedRouteUsageDirectionInfos.put(direction, 1);
        } else {
            this.unexpectedRouteUsageDirectionInfos.put(direction, this.unexpectedRouteUsageDirectionInfos.get(direction) + 1);
        }
    }

    public int getUnexpectedRouteUsedClusterNum() {
        return unexpectedRouteUsedClusterNum;
    }

    public UnexpectedRouteUsageInfoModel setUnexpectedRouteUsedClusterNum(int unexpectedRouteUsedClusterNum) {
        this.unexpectedRouteUsedClusterNum = unexpectedRouteUsedClusterNum;
        return this;
    }

    public Map<String, Integer> getUnexpectedRouteUsageDirectionInfos() {
        return unexpectedRouteUsageDirectionInfos;
    }

    public UnexpectedRouteUsageInfoModel setUnexpectedRouteUsageDirectionInfos(Map<String, Integer> unexpectedRouteUsageDirectionInfos) {
        this.unexpectedRouteUsageDirectionInfos = unexpectedRouteUsageDirectionInfos;
        return this;
    }

    public Map<String, List<UnexpectedRouteUsageInfo>> getUnexpectedRouteUsageDetailInfos() {
        return unexpectedRouteUsageDetailInfos;
    }

    public UnexpectedRouteUsageInfoModel setUnexpectedRouteUsageDetailInfos(Map<String, List<UnexpectedRouteUsageInfo>> unexpectedRouteUsageDetailInfos) {
        this.unexpectedRouteUsageDetailInfos = unexpectedRouteUsageDetailInfos;
        return this;
    }

    public static class UnexpectedRouteUsageInfo {
        private String clusterName;

        private String srcDcName;

        private String dstDcName;

        private Set<Integer> usedRouteId;

        private int chooseRouteId;

        public UnexpectedRouteUsageInfo() {
        }

        public UnexpectedRouteUsageInfo(String clusterName, String srcDcName, String dstDcName, Set<Integer> usedRouteId, int chooseRouteId) {
            this.clusterName = clusterName;
            this.srcDcName = srcDcName;
            this.dstDcName = dstDcName;
            this.usedRouteId = usedRouteId;
            this.chooseRouteId = chooseRouteId;
        }

        public String getClusterName() {
            return clusterName;
        }

        public UnexpectedRouteUsageInfo setClusterName(String clusterName) {
            this.clusterName = clusterName;
            return this;
        }

        public String getSrcDcName() {
            return srcDcName;
        }

        public UnexpectedRouteUsageInfo setSrcDcName(String srcDcName) {
            this.srcDcName = srcDcName;
            return this;
        }

        public String getDstDcName() {
            return dstDcName;
        }

        public UnexpectedRouteUsageInfo setDstDcName(String dstDcName) {
            this.dstDcName = dstDcName;
            return this;
        }

        public Set<Integer> getUsedRouteId() {
            return usedRouteId;
        }

        public UnexpectedRouteUsageInfo setUsedRouteId(Set<Integer> usedRouteId) {
            this.usedRouteId = usedRouteId;
            return this;
        }

        public int getChooseRouteId() {
            return chooseRouteId;
        }

        public UnexpectedRouteUsageInfo setChooseRouteId(int chooseRouteId) {
            this.chooseRouteId = chooseRouteId;
            return this;
        }

        @Override
        public String toString() {
            return "UnexpectedRouteUsageInfo{" +
                    "clusterName='" + clusterName + '\'' +
                    ", srcDcName='" + srcDcName + '\'' +
                    ", dstDcName='" + dstDcName + '\'' +
                    ", usedRouteId=" + usedRouteId +
                    ", chooseRouteId=" + chooseRouteId +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UnexpectedRouteUsageInfo that = (UnexpectedRouteUsageInfo) o;
            return usedRouteId == that.usedRouteId && chooseRouteId == that.chooseRouteId && Objects.equals(clusterName, that.clusterName)
                    && Objects.equals(srcDcName, that.srcDcName) && Objects.equals(dstDcName, that.dstDcName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clusterName, srcDcName, dstDcName, usedRouteId, chooseRouteId);
        }
    }
}
