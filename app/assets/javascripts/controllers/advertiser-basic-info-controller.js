/**
 * Created by weizheng on 15/1/31.
 */

angular.module('m8chatAdminApp.controllers')
    .controller('AdvertiserBasicInfoController', ['$scope', '$window', '$modal', function ($scope, $window, $modal) {
        $scope.suspend = function(advertiserId) {
            var modalInstance = $modal.open({
                templateUrl: '/assets/javascripts/templates/advertiser-suspend-model.html',
                controller: 'AdvertiserSuspendController',
                resolve: {
                    advertiserId: function () {
                        return advertiserId;
                    }
                },
                backdrop: 'static'
            });

            modalInstance.result.then(function (result) {
                if (result == 'suspended') {
                    $window.location.reload();
                }
            });
        };

        $scope.resume = function(advertiserId) {
            var modalInstance = $modal.open({
                templateUrl: '/assets/javascripts/templates/advertiser-resume-model.html',
                controller: 'AdvertiserResumeController',
                resolve: {
                    advertiserId: function () {
                        return advertiserId;
                    }
                },
                backdrop: 'static'
            });

            modalInstance.result.then(function (result) {
                if (result == 'resumed') {
                    $window.location.reload();
                }
            });
        };
    }]);