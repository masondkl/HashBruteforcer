Example project to show the realtime usage of Plinth, which is a networking library that I am creating alongside a game.

The server generates a secret which is LENGTH characters long using characters from a set of VALID.

The server then divides all combinations(VALID.size^LENGTH) into chunks of combinations ÷ active_nodes.
The final node will be left with the remainder, rather than a full chunk.

The server sends a packet to active nodes with a task id, hashed secret, offset, and count.

The node will then perform the task of walking through a subset of all possible secrets as represented by this psuedocode:

```
for c in offset..<(offset+count):
    secret := char[LENGTH]
    combo := c
    for i in 0..<LENGTH: 
        secret[i] := VALID[combo % VALID.size]
        combo /= VALID.size
    if secret == server_secret:
        return secret
return null
```

The node will send a packet to the server with a task id and a secret.

If the task id matches the server task id and the secret matches the server secret then the server sends an end packet to all nodes and prints a result.

If the task takes longer than TIMEOUT, then the server sends an end packet to all nodes.