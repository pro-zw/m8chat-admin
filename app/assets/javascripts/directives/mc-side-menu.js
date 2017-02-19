/**
 * Created by weizheng on 15/1/29.
 */

angular.module('m8chatAdminApp.directives')
    .directive('mcSideMenu', function() {
        return {
            restrict: 'A',
            link: function(scope, element) {
                element.metisMenu({
                    toggle: false
                });
            }
        };
    });