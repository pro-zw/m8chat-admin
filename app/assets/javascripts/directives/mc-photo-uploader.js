/**
 * Created by weizheng on 15/2/2.
 */

angular.module('m8chatAdminApp.directives')
    .directive('mcPhotoUploader', ['$http', '$location', 'alertService', 'appUtil', function($http, $location, alertService, appUtil) {
        return {
            templateUrl: '/assets/javascripts/templates/photo-uploader.html',
            restrict: 'A',
            replace: true,
            scope: {
                photoIndex: '@',
                photoSrc: '@'
            },
            link: function(scope, element) {
                var imageElement = element.children("img");
                scope.isUploading = false;
                scope.isDeleting = false;
                scope.isPhotoEmpty = true;

                scope.updatePhotoClick = function(event) {
                    if (event) {
                        event.preventDefault();
                    }
                    element.children("input[type='file']").trigger('click');
                };

                scope.deletePhotoClick = function(event) {
                    if (event) {
                        event.preventDefault();
                    }

                    scope.isDeleting = true;
                    $http.delete($location.absUrl() + '/' + scope.photoIndex)
                        .success(function () {
                            scope.isDeleting = false;
                            imageElement.attr('src', '/assets/images/photo-empty.png');
                        })
                        .error(function (data, status) {
                            scope.isDeleting = false;
                            alertService.setAlert('photo-uploader-' + scope.photoIndex, appUtil.parseErrorMessage(data, status), 'alert-danger');
                        });
                };

                scope.uploadPhoto = function(files) {
                    if (files[0]) {
                        var formData = new FormData();
                        formData.append("photo", files[0]);

                        scope.isUploading = true;

                        $http.post($location.absUrl() + '/' + scope.photoIndex, formData, {
                            headers: {
                                'Content-Type': undefined,
                                'Csrf-Token': $('input[name="csrfToken"]').val()
                            },
                            transformRequest: angular.identity
                        }).success(function (data) {
                            scope.isUploading = false;
                            if (data && data.photoPath) {
                                imageElement.attr('src', '//' + data.photoPath);
                            }
                        }).error(function (data, status) {
                            scope.isUploading = false;
                            alertService.setAlert('photo-uploader-' + scope.photoIndex, appUtil.parseErrorMessage(data, status), 'alert-danger');
                        });
                    }
                };

                scope.$watch(function() {
                    return imageElement.attr('src');
                }, function(imageSrc) {
                    scope.isPhotoEmpty = (imageSrc.indexOf("photo-empty.png") > -1);
                });
            }
        };
    }]);