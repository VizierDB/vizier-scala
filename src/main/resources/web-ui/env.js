// COPIED FROM:
// https://github.com/facebookincubator/create-react-app/issues/578
//
// This file will not end up inside the main application JavaScript bundle.
// Instead, it will simply be copied inside the build folder.
// The generated "index.html" will require it just before this main bundle.
// You can thus use it to define some environment variables that will
// be made available synchronously in all your JS modules under "src".
//
// Warning: this file will not be transpiled by Babel and cannot contain
// any syntax that is not yet supported by your targeted browsers.

window.env = {
  // This option can be retrieved in "src/index.js" with "window.env.API_URL".
  API_URL: 'http://localhost:5000/vizier-db/api/v1/',
  API_BASIC_AUTH: false,
  APP_TITLE: 'Development Server',
  ANALYTICS_URL: '',
  ANALYTICS_SITE_ID: '12a12e629ffb388167c2c3e560bbc8e1'
};
