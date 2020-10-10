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
    <div style="display:none;"><img id="b-pawn" src="/static/b-pawn.png" width="100" height="100" alt="black-pawn"></div>
    <div style="display:none;"><img id="b-bishop" src="/static/b-bishop.png" width="100" height="100" alt="black-bishop"></div>
    <div style="display:none;"><img id="b-knight" src="/static/b-knight.png" width="100" height="100" alt="black-knight"></div>
    <div style="display:none;"><img id="b-rook" src="/static/b-rook.png" width="100" height="100" alt="black-rook"></div>
    <div style="display:none;"><img id="b-queen" src="/static/b-queen.png" width="100" height="100" alt="black-queen"></div>
    <div style="display:none;"><img id="b-king" src="/static/b-king.png" width="100" height="100" alt="black-king"></div>

    <div style="display:none;"><img id="w-pawn" src="/static/w-pawn.png" width="100" height="100" alt="white-pawn"></div>
    <div style="display:none;"><img id="w-bishop" src="/static/w-bishop.png" width="100" height="100" alt="white-bishop"></div>
    <div style="display:none;"><img id="w-knight" src="/static/w-knight.png" width="100" height="100" alt="white-knight"></div>
    <div style="display:none;"><img id="w-rook" src="/static/w-rook.png" width="100" height="100" alt="white-rook"></div>
    <div style="display:none;"><img id="w-queen" src="/static/w-queen.png" width="100" height="100" alt="white-queen"></div>
    <div style="display:none;"><img id="w-king" src="/static/w-king.png" width="100" height="100" alt="white-king"></div>
</body>
</html>