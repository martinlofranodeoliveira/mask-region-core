package br.com.lofranotecnologia.maskregion;

/** Foreground adjacency. Hole filling always uses the other adjacency for background. */
public enum Connectivity {
    FOUR, EIGHT;

    public Connectivity dual() {
        return this == FOUR ? EIGHT : FOUR;
    }
}
