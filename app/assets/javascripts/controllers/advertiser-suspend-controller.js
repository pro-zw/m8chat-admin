/**
 * Created by weizheng on 15/1/31.
 */

angular.module('m8chatAdminApp.controllers')
    .controller('AdvertiserSuspendController', ['$scope', '$http', '$modalInstance', 'alertService', 'appUtil', 'advertiserId', function ($scope, $http, $modalInstance, alertService, appUtil, advertiserId) {
        $scope.isSuspending = false;

        $scope.ok = function() {
          $scope.isSuspending = true;

          $http.post('/advertisers/' + advertiserId + '/suspend', {
             reason: $scope.suspendModel.reason
          }).success(function () {
              $scope.isSuspending = false;
              alertService.setAlert('suspend-advertiser-alert', 'Suspended!', 'alert-success');
              $modalInstance.close('suspended');
          }).error(function (data, status) {
              $scope.isSuspending = false;
              alertService.setAlert('suspend-advertiser-alert', appUtil.parseErrorMessage(data, status), 'alert-danger');
          });
        };

        $scope.cancel = function() {
            $modalInstance.dismiss('canceled');
        };
    }]);