angular
    .module('index')
    .controller('ClusterFromCtl', ClusterFromCtl);

ClusterFromCtl.$inject = ['$rootScope', '$scope', '$stateParams', '$window',
    'toastr', 'AppUtil', 'ClusterService', 'DcService', 'SentinelService', 'ClusterType'];

function ClusterFromCtl($rootScope, $scope, $stateParams, $window,
                        toastr, AppUtil, ClusterService, DcService, SentinelService, ClusterType) {

    $rootScope.currentNav = '1-3';

    var OPERATE_TYPE = {
        CREATE: 'create',
        UPDATE: 'update',
        RETRIEVE: 'retrieve'
    };

    var clusterName = $stateParams.clusterName;

    $scope.operateType = $stateParams.type;
    $scope.allDcs = [];
    $scope.selectedDcs = [];
    $scope.shards = [];
    $scope.currentShard = {};
    $scope.sentinels = {};
    $scope.organizations = [];
    $scope.organizationNames = [];
    $scope.clusterTypeName = undefined;

    $scope.dcClusterModels = [];
    $scope.toCreateDcGroups = [];
    $scope.groupTypes = ['Master', 'DRMaster'];
    $scope.groupNames = {};
    $scope.allDcNames = [];
    $scope.toUpdateDcGroup = [];
    $scope.toCreateReplDirections = [];
    $scope.replDirections = [];
    $scope.drMasterShards = [];
    $scope.masterShards = [];
    $scope.masterShardNum = {};
    $scope.drMasterDcs = [];
    $scope.activeDcName = '';

    $scope.doCluster = doCluster;
    $scope.getDcName = getDcName;
    $scope.preDeleteCluster = preDeleteCluster;
    $scope.deleteCluster = deleteCluster;
    $scope.slaveExists = slaveExists;
    $scope.toggle = toggle;
    $scope.preCreateShard = preCreateShard;
    $scope.createShard = createShard;
    $scope.deleteShard = deleteShard;
    $scope.activeDcSelected = activeDcSelected;
    $scope.shardNameChanged = shardNameChanged;

    $scope.showActiveDc = true
    $scope.clusterTypes = ClusterType.values()
    $scope.selectedType = ClusterType.default().value
    $scope.typeChange = typeChange;

    $scope.preCreateDcGroup = preCreateDcGroup;
    $scope.createDcGroup = createDcGroup;
    $scope.deleteDcGroup = deleteDcGroup;
    $scope.preUpdateDcGroup = preUpdateDcGroup;
    $scope.updateDcGroup = updateDcGroup;
    $scope.removeToCreateDcGroups = removeToCreateDcGroups;
    $scope.addOtherDcGroup = addOtherDcGroup;
    $scope.preUpdateDrMasterShardModel = preUpdateDrMasterShardModel;
    $scope.preUpdateMasterShardModel = preUpdateMasterShardModel;
    $scope.updateShardModel = updateShardModel;

    $scope.preCreateReplDirection = preCreateReplDirection;
    $scope.createReplDirection = createReplDirection;
    $scope.deleteReplDirection = deleteReplDirection;
    $scope.removeToCreateReplDirections = removeToCreateReplDirections;
    $scope.addOtherReplDirection = addOtherReplDirection;

    init();

    function init() {

       DcService.loadAllDcs()
           .then(function (result) {
               $scope.allDcs = result;
               $scope.allDcs.forEach(function(dc) {
                    var dcGroupNames = [dc.dcName, 'SHA-CTRIP-PRIV'];
                    $scope.groupNames[dc.dcName] = dcGroupNames;
               	    SentinelService.findSentinelsByDc(dc.dcName)
                        .then(function(result) {
                            $scope.sentinels[dc.dcName] = result;
                        });
               });

               $scope.allDcNames = result.map(function (dc) {
                   return dc.dcName;
               });

           });
       ClusterService.getOrganizations()
       .then(function (result) {
            $scope.organizations = result;
           $scope.organizationNames = result.map(function (org) {
               return org.orgName;
           });
            console.log($scope.organizationNames);
        });

        if ($scope.operateType != OPERATE_TYPE.CREATE) {
            ClusterService.load_cluster(clusterName)
                .then(function (result) {
                    $scope.cluster = result;
                    var clusterType = ClusterType.lookup(result.clusterType)
                    $scope.clusterTypeName = clusterType.name
                    $scope.selectedType = clusterType.value
                    $scope.showActiveDc = !clusterType.multiActiveDcs
                }, function (result) {
                    toastr.error(AppUtil.errorMsg(result));
                })
        } else {
            $scope.cluster = {};
            $scope.clusterRelatedDcs = [];
        }
    }

    function doCluster() {
        if ($scope.operateType == OPERATE_TYPE.CREATE) {
       	 $scope.shards.forEach(function(shard) {
       		shard.shardTbl = {};
       		shard.shardTbl.shardName = shard.shardName;
       		shard.shardTbl.setinelMonitorName = shard.setinelMonitorName;
       	 });

       	 $scope.dcClusterModels.forEach(function (dcClusterModel) {
       	    if (dcClusterModel.dcCluster.groupType == $scope.groupTypes[1]) {
       	        dcClusterModel.dcCluster.groupType = true;
       	        dcClusterModel.shards = [];
       	        $scope.drMasterShards.forEach(function(shard){
       	            dcClusterModel.shards.push({
       	                "shardTbl" : {
       	                    "shardName" : shard.shardName,
       	                    "setinelMonitorName" : shard.setinelMonitorName
       	                }
       	            })
       	        });
       	    } else if (dcClusterModel.dcCluster.groupType == $scope.groupTypes[0]){
       	        dcClusterModel.dcCluster.groupType = false;
                dcClusterModel.shards = [];
       	        $scope.masterShards[dcClusterModel.dcCluster.groupName].forEach(function(shard){
                    dcClusterModel.shards.push({
                        "shardTbl" : {
                            "shardName" : shard.shardName,
                            "setinelMonitorName" : shard.setinelMonitorName
                        }
                    })
                });
       	    }
       	 });

       	 if ($scope.activeDcName != '' && $scope.selectedType == 'hetero') {
       	    var dcId = getDcId($scope.activeDcName);
       	    if (dcId == -1) {
       	        toastr.error("activeDcName" + $scope.activeDcName + "is not exist", "创建失败");
       	    }
       	    $scope.cluster.activedcId = getDcId($scope.activeDcName);
       	 }

       	 $scope.cluster.clusterType = $scope.selectedType
            ClusterService.createCluster($scope.cluster, $scope.clusterRelatedDcs, $scope.shards, $scope.dcClusterModels, $scope.replDirections)
                .then(function (result) {
                    toastr.success("创建成功");
                    $window.location.href =
                        "/#/cluster_form?clusterName=" + result.clusterName + "&type=retrieve";
                }, function (result) {
                    toastr.error(AppUtil.errorMsg(result), "创建失败");
                    $scope.dcClusterModels.forEach(function (dcClusterModel) {
                        if (dcClusterModel.dcCluster.groupType == true) {
                            dcClusterModel.dcCluster.groupType = $scope.groupTypes[1];
                        } else if (dcClusterModel.dcCluster.groupType == false) {
                            dcClusterModel.dcCluster.groupType = $scope.groupTypes[0];
                        }
                    });
                });
        } else {
            ClusterService.updateCluster($scope.cluster.clusterName, $scope.cluster)
                .then(function (result) {
                    toastr.success("更新成功");
                    $window.location.href =
                        "/#/cluster_form?clusterName=" + result.clusterName + "&type=retrieve";
                }, function (result) {
                    toastr.error(AppUtil.errorMsg(result), "更新失败");
                });
        }

    }

    function getDcName(dcId) {
        var result = '';
        $scope.allDcs.forEach(function (dc) {
            if (dc.id == dcId){
                result = dc.dcName;
                return;
            }
        });
        return result;
    }

    function getDcId(dcName) {
        var result = -1;
        $scope.allDcs.forEach(function (dc) {
            if (dc.dcName == dcName){
                result = dc.id;
                return;
            }
        });
        return result;
    }

    function preDeleteCluster() {
        $('#deleteClusterConfirm').modal('show');

    }
    function deleteCluster(cluster) {
        ClusterService.deleteCluster($scope.cluster.clusterName)
            .then(function (result) {
                $('#deleteClusterConfirm').modal('hide');
                toastr.success('删除成功');
                setTimeout(function () {
                    $window.location.href = '/#/cluster_list';
                },1000);


            }, function (result) {
                toastr.error(AppUtil.errorMsg(result), '删除失败');
            })
    }

    function slaveExists(dc) {
   	 return $scope.selectedDcs.indexOf(dc) > -1;
    }

    function toggle(dc) {
   	var idx = $scope.selectedDcs.indexOf(dc);
	    if (idx > -1) {
	    	$scope.selectedDcs.splice(idx, 1);
	    	var clusterRelatedDcIdx = $scope.clusterRelatedDcs.indexOf(dc);
	    	if(clusterRelatedDcIdx > -1) {
	    		$scope.clusterRelatedDcs.splice(clusterRelatedDcIdx,1);
	    	}
	    }
	    else {
	    	$scope.selectedDcs.push(dc);
	    	$scope.clusterRelatedDcs.push(dc);
	    }
    }

    function preCreateShard() {
        $('#createShardModal').modal('show');
    }

    function createShard() {
        var shardSentinels = {};
        for(var key in $scope.currentShard.sentinels) {
       	 shardSentinels[key] = $scope.currentShard.sentinels[key];
        }
    	$scope.shards.push({
    		shardName: $scope.currentShard.shardName,
    		setinelMonitorName: $scope.currentShard.setinelMonitorName,
           sentinels : shardSentinels
    	});
    	$('#createShardModal').modal('hide');
    }

    function deleteShard(shardName) {
    	for(var i in $scope.shards) {
    		if($scope.shards[i].shardName == shardName) {
    			$scope.shards.splice(i, 1);
    			break;
    		}
    	}
    }

    function activeDcSelected(toPush) {
   	 var forDeleted = [];
   	 $scope.clusterRelatedDcs.forEach(function(dc) {
   		if($scope.selectedDcs.indexOf(dc) == -1) {
   			forDeleted.push(dc);
   		}
   	 });

   	 forDeleted.forEach(function(forDeleted) {
   		var idx = $scope.clusterRelatedDcs.indexOf(forDeleted);
   		if(idx > -1) {
   			$scope.clusterRelatedDcs.splice(idx,1);
   		}
   	 });

   	 $scope.clusterRelatedDcs.push(toPush);
    }

    function shardNameChanged() {
   	 if ($scope.cluster) {
   	     if ($scope.selectedType != 'hetero' && $scope.currentShard) {
   		     if ($scope.currentShard.shardName.indexOf($scope.cluster.clusterName) >=0 ){
   			    $scope.currentShard.setinelMonitorName = $scope.currentShard.shardName;
   			 } else {
   			    $scope.currentShard.setinelMonitorName = $scope.cluster.clusterName + $scope.currentShard.shardName;
   			 }
   	     }
   		 if ($scope.selectedType == 'hetero' && $scope.toUpdateShardModel) {
   		     if ($scope.toUpdateShardModel.shardName.indexOf($scope.cluster.clusterName) >=0 ){
   			    $scope.toUpdateShardModel.setinelMonitorName = $scope.toUpdateShardModel.shardName;
   			 } else {
   			    $scope.toUpdateShardModel.setinelMonitorName = $scope.cluster.clusterName + $scope.toUpdateShardModel.shardName;
   			 }
   		 }
   	 }
    }

    function typeChange() {
        $scope.showActiveDc = !ClusterType.lookup($scope.selectedType).multiActiveDcs
        if (!$scope.showActiveDc) {
            var activeDc = $scope.allDcs.find(dc => dc.id === $scope.cluster.activedcId)
            var clusterRelatedDcIdx = $scope.clusterRelatedDcs.indexOf(activeDc);
            var selectedDcIdx = $scope.selectedDcs.indexOf(activeDc)
            if(clusterRelatedDcIdx > -1) {
                $scope.clusterRelatedDcs.splice(clusterRelatedDcIdx,1);
            }
            if (selectedDcIdx > -1) {
                $scope.selectedDcs.splice(selectedDcIdx, 1)
            }
            $scope.cluster.activedcId = undefined
        }
    }

    function preCreateDcGroup() {
        $scope.toCreateDcGroups=[];
        $scope.toCreateDcGroups.push({
            "dc":  {
                "dc_name":$scope.allDcNames[0]
            },
            "dcCluster" : {
                "groupName": $scope.allDcNames[0],
                "groupType": $scope.groupTypes[1]
            },
            "shardNum": 0
        })
        $('#createDcGroupModal').modal('show');
    }

    function createDcGroup() {
        $scope.toCreateDcGroups.forEach(function(toCreateDcGroup){
            if (!isAlreadyExistDcGroup(toCreateDcGroup.dc.dc_name)) {
                $scope.dcClusterModels.push(toCreateDcGroup);
                if (toCreateDcGroup.dcCluster.groupType == $scope.groupTypes[1]) {
                    $scope.drMasterDcs.push(toCreateDcGroup.dc.dc_name);
                    $scope.drMasterShardNum = toCreateDcGroup.shardNum;
                    updateDrMasterShard();
                } else if (toCreateDcGroup.dcCluster.groupType == $scope.groupTypes[0]){
                    $scope.masterShardNum[toCreateDcGroup.dcCluster.groupName] = toCreateDcGroup.shardNum;
                    updateMasterShard(toCreateDcGroup.dcCluster.groupName);
                    updateAllMasterShards();
                }
            }
        });

        $('#createDcGroupModal').modal('hide');
    }

    function isAlreadyExistDcGroup(dcName) {
        var exist = false;
        for (var index = 0; index < $scope.dcClusterModels.length; index++) {
            if ($scope.dcClusterModels[index].dc.dc_name == dcName) {
               exist = true;
               break;
            }
        }
        return exist;
    }

    function deleteDcGroup(index) {
        $scope.needCheckDrMasterShards = false;
        if ($scope.dcClusterModels[index].dcCluster.groupType == $scope.groupTypes[0]){
            $scope.masterShardNum[$scope.dcClusterModels[index].dcCluster.groupName] = 0;
            updateMasterShard($scope.dcClusterModels[index].dcCluster.groupName);
        } else if ($scope.dcClusterModels[index].dcCluster.groupType == $scope.groupTypes[1]){
            removeDcFromDrMasterDcs($scope.dcClusterModels[index].dc.dc_name);
            $scope.needCheckDrMasterShards = true;
        }
        $scope.dcClusterModels.splice(index, 1);
        updateAllMasterShards();
        updateAllDrMasterShards($scope.needCheckDrMasterShards);
    }

    function removeDcFromDrMasterDcs(dcName) {
        var index = -1;
        for (var i = 0; i < $scope.drMasterDcs.length; i++) {
            if ($scope.drMasterDcs[i] == dcName) {
                index = i;
                break;
            }
        }

        if (index != -1) {
            $scope.drMasterDcs.splice(i, 1);
        }
    }
    function preUpdateDcGroup(dcName) {
        $scope.toUpdateDcGroup=[];
        for(var i in $scope.dcClusterModels) {
            if($scope.dcClusterModels[i].dc.dc_name == dcName) {
                $scope.toUpdateDcGroup = {
                     "dc":  {
                         "dc_name":$scope.dcClusterModels[i].dc.dc_name
                     },
                     "dcCluster" : {
                         "groupName": $scope.dcClusterModels[i].dcCluster.groupName,
                         "groupType": $scope.dcClusterModels[i].dcCluster.groupType
                     },
                     "shardNum": $scope.dcClusterModels[i].shardNum
                 };
                break;
            }
        }

        $('#updateDcGroupModal').modal('show');
    }

    function updateDcGroup() {
        $scope.needCheckDrMasterShards = false;
        for(var i in $scope.dcClusterModels) {
            if($scope.dcClusterModels[i].dc.dc_name == $scope.toUpdateDcGroup.dc.dc_name) {
                if ($scope.toUpdateDcGroup.dcCluster.groupType == $scope.groupTypes[1]) {
                    if ($scope.toUpdateDcGroup.dcCluster.groupType != $scope.dcClusterModels[i].dcCluster.groupType) { //Master ---> DrMaster
                        $scope.masterShardNum[$scope.dcClusterModels[i].dcCluster.groupName] = 0;
                        updateMasterShard($scope.toUpdateDcGroup.dcCluster.groupName);
                        $scope.drMasterDcs.push($scope.toUpdateDcGroup.dc.dc_name);
                    }
                    $scope.drMasterShardNum = $scope.toUpdateDcGroup.shardNum;
                    updateDrMasterShard();
                } else if ($scope.toUpdateDcGroup.dcCluster.groupType == $scope.groupTypes[0]){
                    if ($scope.toUpdateDcGroup.dcCluster.groupType != $scope.dcClusterModels[i].dcCluster.groupType) {//DrMaster ---> Master
                        $scope.needCheckDrMasterShards = true;
                        removeDcFromDrMasterDcs($scope.toUpdateDcGroup.dc.dc_name);
                    }
                    $scope.masterShardNum[$scope.toUpdateDcGroup.dcCluster.groupName] = $scope.toUpdateDcGroup.shardNum;
                    updateMasterShard($scope.toUpdateDcGroup.dcCluster.groupName);
                }
                $scope.dcClusterModels[i] = $scope.toUpdateDcGroup;
                updateAllMasterShards();
                updateAllDrMasterShards($scope.needCheckDrMasterShards);
                break;
            }
        }

        $('#updateDcGroupModal').modal('hide');
    }

    function removeToCreateDcGroups(index) {
        $scope.toCreateDcGroups.splice(index, 1);
    }

    function addOtherDcGroup() {
        $scope.toCreateDcGroups.push({});
    }

    function updateDrMasterShard() {
        if ($scope.drMasterShardNum > $scope.drMasterShards.length) {
            for (var i = $scope.drMasterShards.length; i < $scope.drMasterShardNum; i++) {
                var shardName = $scope.cluster.clusterName + '_' + (i + 1);
                $scope.drMasterShards.push({
                    "shardName" : shardName,
                    "setinelMonitorName" : shardName
                });
            }
        } else if ($scope.drMasterShardNum < $scope.drMasterShards.length) {
            var len = $scope.drMasterShards.length - $scope.drMasterShardNum;
            $scope.drMasterShards.splice($scope.drMasterShardNum, len);
        }

        $scope.dcClusterModels.forEach(function(dcClusterModel) {
            if (dcClusterModel.dcCluster.groupType == 'DRMaster') {
                dcClusterModel.shardNum = $scope.drMasterShardNum;
            }
        });
    }

    function updateMasterShard(groupName) {
        if (typeof($scope.masterShards[groupName]) == 'undefined') {
            $scope.masterShards[groupName] = [];
            for (var i = 0; i < $scope.masterShardNum[groupName] ; i++) {
                var shardName = $scope.cluster.clusterName + '_' + groupName + '_' + (i + 1);
                $scope.masterShards[groupName].push({
                    "shardGroup" : groupName,
                    "groupIndex" : i,
                    "shardName" : shardName,
                    "setinelMonitorName" : shardName
                });
            }
        } else if ($scope.masterShardNum[groupName] > $scope.masterShards[groupName].length) {
            for (var i:number =$scope.masterShards[groupName].length; i < $scope.masterShardNum[groupName]; i++) {
                var shardName = $scope.cluster.clusterName + '_' + groupName + '_' + (i + 1);
                $scope.masterShards[groupName].push({
                    "shardGroup" : groupName,
                    "groupIndex" : i,
                    "shardName" : shardName,
                    "setinelMonitorName" : shardName
                });
            }
        }else if ($scope.masterShardNum[groupName] < $scope.masterShards[groupName].length) {
            var len = $scope.masterShards[groupName].length - $scope.masterShardNum[groupName];
            $scope.masterShards[groupName].splice($scope.masterShardNum[groupName], len);
        }
    }

    function updateAllMasterShards() {
        $scope.allMasterShards = [];
        $scope.dcClusterModels.forEach(function(dcClusterModel) {
            if (dcClusterModel.dcCluster.groupType == $scope.groupTypes[0]) {
                $scope.masterShards[dcClusterModel.dcCluster.groupName].forEach(function(masterShard) {
                   $scope.allMasterShards.push(masterShard);
                });
            }
        });
    }

    function updateAllDrMasterShards(needCheckDrMasterShards) {
        if (needCheckDrMasterShards && !isExistDrMaster()) {
            $scope.drMasterShardNum = 0;
            $scope.drMasterShards = [];
        }
    }

    function isExistDrMaster() {
        for(var i in $scope.dcClusterModels) {
            if ($scope.dcClusterModels[i].dcCluster.groupType == $scope.groupTypes[1]) {
                return true;
            }
        }
        return false;
    }

    function preUpdateDrMasterShardModel(index) {
        $scope.toUpdateShardModel = [];
        $scope.toUpdateShardModel = {
            "shardType" : $scope.groupTypes[1],
            "shardIndex": index,
            "shardName" : $scope.drMasterShards[index].shardName,
            "setinelMonitorName" : $scope.drMasterShards[index].setinelMonitorName
        }

        $('#updateShardModal').modal('show');
    }

    function preUpdateMasterShardModel(index) {
        $scope.toUpdateShardModel = [];
        $scope.toUpdateShardModel = {
            "shardType" : $scope.groupTypes[0],
            "shardIndex": index,
            "shardGroup" : $scope.allMasterShards[index].shardGroup,
            "groupIndex" : $scope.allMasterShards[index].groupIndex,
            "shardName" : $scope.allMasterShards[index].shardName,
            "setinelMonitorName" : $scope.allMasterShards[index].setinelMonitorName
        }

        $('#updateShardModal').modal('show');
    }

    function updateShardModel() {
        if ($scope.toUpdateShardModel.shardType == $scope.groupTypes[0]) {
            var newMasterShardIndexModel = {
                "shardGroup" : $scope.toUpdateShardModel.shardGroup,
                "groupIndex" : $scope.toUpdateShardModel.groupIndex,
                "shardName" : $scope.toUpdateShardModel.shardName,
                "setinelMonitorName" : $scope.toUpdateShardModel.setinelMonitorName
            }

            $scope.masterShards[$scope.toUpdateShardModel.shardGroup][$scope.toUpdateShardModel.groupIndex] = newMasterShardIndexModel;
            $scope.allMasterShards[$scope.toUpdateShardModel.shardIndex] = newMasterShardIndexModel;

        } else if ($scope.toUpdateShardModel.shardType == $scope.groupTypes[1]) {
            $scope.drMasterShards[$scope.toUpdateShardModel.shardIndex] = {
                "shardName" : $scope.toUpdateShardModel.shardName,
                "setinelMonitorName" : $scope.toUpdateShardModel.setinelMonitorName
            }
        }
        $('#updateShardModal').modal('hide');
    }


    function preCreateReplDirection() {
        $scope.toCreateReplDirections=[];
        $scope.toCreateReplDirections.push({
            "clusterName": $scope.cluster.clusterName,
            "srcDcName": $scope.allDcNames[0],
            "fromDcName": $scope.allDcNames[0],
            "toDcName": $scope.allDcNames[0],
        })
        $('#createReplDirectionModal').modal('show');
    }

    function createReplDirection() {
        $scope.toCreateReplDirections.forEach(function(toCreateReplDirection){
            $scope.replDirections.push(toCreateReplDirection);
        });

        $('#createReplDirectionModal').modal('hide');
    }

    function deleteReplDirection(index) {
        $scope.replDirections.splice(index, 1);
    }

    function removeToCreateReplDirections(index) {
        $scope.toCreateReplDirections.splice(index, 1);
    }

    function addOtherReplDirection() {
        $scope.toCreateReplDirections.push({
            "clusterName": $scope.cluster.clusterName
        });
    }
}
