<!DOCTYPE html>
<html lang="en">
<head>
    <script src="/static/jquery.min.js"></script>
    <script src="/static/createPage.js"></script>
    <meta charset="UTF-8">
    <title>Create game</title>
</head>
<body>
<label for="gk">
    Choose game kind
</label>
<select id="gk">
    <option value="QUANTUM_CHESS">Quantum chess</option>
    <option value="CLASSIC_CHESS">Classic chess</option>
    <option value="CHECKERS">Checkers</option>
    <option value="SIMPLE">Simple</option>
</select>
<input type="button" id="create-btn" value="Create">
</body>
</html>