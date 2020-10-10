<!DOCTYPE html>
<html lang="en">
<head>
    <script>
        let gameId = ${id};
        let gameKind = "${kind}";
    </script>
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.5.1/jquery.min.js"></script>
    <script src="/static/playPage.js"></script>
    <meta charset="UTF-8">
    <title>Play</title>
</head>
<body>
    <p>
        Id: ${id}<br>
        Kind: ${kind}
    </p>
    <canvas id="board" width="800" height="800" style="border:1px solid #000000;"></canvas>
</body>
</html>