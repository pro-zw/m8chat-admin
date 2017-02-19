/**
 * Created by weizheng on 15/1/23.
 */

angular.module('m8chatAdminApp.filters', []);

angular.module('m8chatAdminApp.services', [
    'm8chatAdminApp.filters'
]);

angular.module('m8chatAdminApp.directives', [
    'm8chatAdminApp.services'
]);

angular.module('m8chatAdminApp.controllers', [
    'm8chatAdminApp.services',
    'm8chatAdminApp.directives'
]);

angular.module('m8chatAdminApp', [
    'ngCookies',
    'ngSanitize',
    'ngTouch',
    'ui.utils',
    'ui.bootstrap',
    'm8chatAdminApp.filters',
    'm8chatAdminApp.services',
    'm8chatAdminApp.directives',
    'm8chatAdminApp.controllers'
]).config(['$httpProvider', '$locationProvider', function($httpProvider, $locationProvider) {
    $httpProvider.defaults.withCredentials = true;
    $locationProvider.html5Mode(false).hashPrefix('!');
}]);