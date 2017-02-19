/**
 * Created by weizheng on 15/1/31.
 */

angular.module('m8chatAdminApp.controllers')
    .controller('AdvertiserResumeController', ['$scope', '$http', '$modalInstance', 'alertService', 'appUtil', 'advertiserId', function ($scope, $http, $modalInstance, alertService, appUtil, advertiserId) {
        $scope.isResuming = false;

        $scope.ok = function() {
            $scope.isResuming = true;

            $http.post('/advertisers/' + advertiserId + '/resume')
                .success(function () {
                    $scope.isResuming = false;
                    alertService.setAlert('resume-advertiser-alert', 'Resumed!', 'alert-success');
                    $modalInstance.close('resumed');
                }).error(function (data, status) {
                    $scope.isResuming = false;
                    alertService.setAlert('resume-advertiser-alert', appUtil.parseErrorMessage(data, status), 'alert-danger');
                });
        };

        $scope.cancel = function() {
            $modalInstance.dismiss('canceled');
        };
    }]);