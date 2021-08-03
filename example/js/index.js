const express = require('express');
const app = express();
const port = 3000;

// Setup some objects to pass to the nrepl server
const root = new Object();
root.response = 'Hello World!';

app.get('/', (req, res) => {
  res.send(root.response);
});

app.listen(port, () => {
  console.log(`Example app listening at http://localhost:${port}`);
});

var nrepl = require('nrepl-cljs-sci');
var nrepl_server = nrepl.start_server(
  {
    // Pass reference to application with the 'app' key
    app: {
      express_app: app,
      root: root
    }
  });
