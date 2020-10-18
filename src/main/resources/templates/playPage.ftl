<!DOCTYPE html>
<html lang="en">
<head>
    <script>
        let gameId = ${id};
        let gameKind = "${kind}";
    </script>
    <link rel="stylesheet" href="/static/main.css">
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.5.1/jquery.min.js"></script>
    <script src="/static/playPage.js"></script>
    <meta charset="UTF-8">
    <title>Play</title>
</head>
<body style="background-color: #ffd37b">
    <p>
        Id: ${id}<br>
        Kind: ${kind}<br>
        CurrentPlayer: <span id="current-player">White</span>
    </p>
    <div id="board-div" style="text-align:center;">
        <canvas id="board" width="800" height="800"></canvas>
    </div>
    <div class="inv"><img id="b-pawn" src="/static/b-pawn.png" width="100" height="100" alt="black-pawn"></div>
    <div class="inv"><img id="b-bishop" src="/static/b-bishop.png" width="100" height="100" alt="black-bishop"></div>
    <div class="inv"><img id="b-knight" src="/static/b-knight.png" width="100" height="100" alt="black-knight"></div>
    <div class="inv"><img id="b-rook" src="/static/b-rook.png" width="100" height="100" alt="black-rook"></div>
    <div class="inv"><img id="b-queen" src="/static/b-queen.png" width="100" height="100" alt="black-queen"></div>
    <div class="inv"><img id="b-king" src="/static/b-king.png" width="100" height="100" alt="black-king"></div>
    <div class="inv"><img id="w-pawn" src="/static/w-pawn.png" width="100" height="100" alt="white-pawn"></div>
    <div class="inv"><img id="w-bishop" src="/static/w-bishop.png" width="100" height="100" alt="white-bishop"></div>
    <div class="inv"><img id="w-knight" src="/static/w-knight.png" width="100" height="100" alt="white-knight"></div>
    <div class="inv"><img id="w-rook" src="/static/w-rook.png" width="100" height="100" alt="white-rook"></div>
    <div class="inv"><img id="w-queen" src="/static/w-queen.png" width="100" height="100" alt="white-queen"></div>
    <div class="inv"><img id="w-king" src="/static/w-king.png" width="100" height="100" alt="white-king"></div>
    <div id="tooltip"></div>
</body>
</html>