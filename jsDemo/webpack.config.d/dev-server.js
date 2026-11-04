// Serve generated resources locally, never the repository root.
if (config.devServer) {
    config.devServer.host = '127.0.0.1';
    config.devServer.open = false;
    config.devServer.allowedHosts = ['127.0.0.1', 'localhost'];
    config.devServer.static = [
        { directory: require('path').resolve(__dirname, 'kotlin'), watch: false },
        { directory: require('path').resolve(__dirname, '../../../../jsDemo/build/processedResources/js/main'), watch: false }
    ];
}
