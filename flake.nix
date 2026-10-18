{
  description = "НТО 2026, второй тур: backend пропускной системы";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    { nixpkgs, ... }:
    let
      supportedSystems = [
        "x86_64-linux"
        "aarch64-linux"
      ];
      forAllSystems = nixpkgs.lib.genAttrs supportedSystems;
    in
    {
      devShells = forAllSystems (
        system:
        let
          pkgs = import nixpkgs { inherit system; };
        in
        {
          default = pkgs.mkShell {
            name = "nto-2026-tour2-backend";

            packages = with pkgs; [
              temurin-bin-17
              postgresql_17
              podman-compose
            ];

            JAVA_HOME = "${pkgs.temurin-bin-17}";
          };
        }
      );
    };
}
