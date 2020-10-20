<!DOCTYPE html>
<html lang="en">
<head>
    <script>
        let gameId = ${id};
        let gameKind = "${kind}";
    </script>
    <link rel="stylesheet" href="/static/main.css">
    <script src="/static/jquery.min.js"></script>
    <script src="/static/playPage.js"></script>
    <meta charset="UTF-8">
    <title>Quantum Chess | Game #${id}</title>

    <meta name="description" content="Quantum chess playground">
    <meta property="og:url" content="${home_url}/game/play?id=${id}">
    <meta property="og:type" content="website">
    <meta property="og:title" content="Quantum Chess | Game #${id}">
    <meta property="og:description" content="Quantum chess playground">
    <meta property="og:image" content="${home_url}/static/chess.png">

    <title>Play</title>
</head>
<body style="background-color: #ffd37b">
    <div id="board-div" style="text-align:center;">
        <canvas id="board" width="800" height="800"></canvas>
    </div>
    <div class="inv"><img id="b-pawn" src="/static/b-pawn.png" alt="black-pawn"></div>
    <div class="inv"><img id="b-bishop" src="/static/b-bishop.png" alt="black-bishop"></div>
    <div class="inv"><img id="b-knight" src="/static/b-knight.png" alt="black-knight"></div>
    <div class="inv"><img id="b-rook" src="/static/b-rook.png" alt="black-rook"></div>
    <div class="inv"><img id="b-queen" src="/static/b-queen.png" alt="black-queen"></div>
    <div class="inv"><img id="b-king" src="/static/b-king.png" alt="black-king"></div>
    <div class="inv"><img id="w-pawn" src="/static/w-pawn.png" alt="white-pawn"></div>
    <div class="inv"><img id="w-bishop" src="/static/w-bishop.png" alt="white-bishop"></div>
    <div class="inv"><img id="w-knight" src="/static/w-knight.png" alt="white-knight"></div>
    <div class="inv"><img id="w-rook" src="/static/w-rook.png" alt="white-rook"></div>
    <div class="inv"><img id="w-queen" src="/static/w-queen.png" alt="white-queen"></div>
    <div class="inv"><img id="w-king" src="/static/w-king.png" alt="white-king"></div>
    <input id="observe-btn" type="button" value="Observe">
    <span id="qs" style="display: none; font-size: 30px; margin-left: 0px">(Quantum step)</span>
    <div id="cur-pl">
        <span id="cpl" class="current-player-lbl">Current player:</span>
        <span id="dot" class="dot"></span>
    </div>
    <div id="tooltip"></div>
</body>
</html>