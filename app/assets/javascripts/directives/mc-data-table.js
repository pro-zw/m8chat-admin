/**
 * Created by weizheng on 15/1/29.
 */

angular.module('m8chatAdminApp.directives')
    .directive('mcDataTable', function() {
        return {
            restrict: 'A',
            scope: {
                orderColumn: '@',
                order: '@'
            },
            link: function(scope, element) {
                element.DataTable({
                    responsive: true,
                    order: [[scope.orderColumn, scope.order]],
                    iDisplayLength: 10
                });
            }
        };
    });