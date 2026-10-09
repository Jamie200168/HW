package com.example.project.data

import org.json.JSONObject

import java.io.File


object OfflineMapStyle {


    // =====================================================
    // 建立 MapLibre Style JSON
    // =====================================================

    fun createStyleJson(
        pmTilesFile: File
    ): String {


        // =================================================
        // MapLibre 要讀手機本機的 PMTiles
        //
        // 最後會類似：
        //
        // pmtiles://file:///data/user/0/
        // com.example.project/files/maps/taiwan.pmtiles
        // =================================================

        val pmTilesUrl =
            "pmtiles://file://${pmTilesFile.absolutePath}"


        // =================================================
        // 防止檔案路徑中的特殊字元破壞 JSON
        // =================================================

        val quotedPmTilesUrl =
            JSONObject.quote(
                pmTilesUrl
            )


        // =================================================
        // MapLibre Style
        // =================================================

        return """
        {
          "version": 8,

          "name": "Taiwan Offline Map",

          "center": [
            121.0,
            23.7
          ],

          "zoom": 7,

          "sources": {

            "taiwan": {

              "type": "vector",

              "url": $quotedPmTilesUrl
            }
          },

          "layers": [


            {
              "id": "background",

              "type": "background",

              "paint": {

                "background-color":
                    "#F4F3EE"
              }
            },


            {
              "id": "landcover",

              "type": "fill",

              "source": "taiwan",

              "source-layer":
                  "landcover",

              "paint": {

                "fill-color":
                    "#DDE8D5",

                "fill-opacity":
                    0.75
              }
            },


            {
              "id": "landuse",

              "type": "fill",

              "source": "taiwan",

              "source-layer":
                  "landuse",

              "paint": {

                "fill-color":
                    "#EEEBDD",

                "fill-opacity":
                    0.55
              }
            },


            {
              "id": "water",

              "type": "fill",

              "source": "taiwan",

              "source-layer":
                  "water",

              "paint": {

                "fill-color":
                    "#B9DDF0"
              }
            },


            {
              "id": "waterway",

              "type": "line",

              "source": "taiwan",

              "source-layer":
                  "waterway",

              "paint": {

                "line-color":
                    "#8FC8E5",

                "line-width": [

                  "interpolate",

                  ["linear"],

                  ["zoom"],

                  7, 0.5,

                  12, 1.2,

                  15, 2.5
                ]
              }
            },


            {
              "id": "boundary",

              "type": "line",

              "source": "taiwan",

              "source-layer":
                  "boundary",

              "paint": {

                "line-color":
                    "#999999",

                "line-width":
                    1.0,

                "line-dasharray":
                    [3, 2]
              }
            },


            {
              "id": "road-casing",

              "type": "line",

              "source": "taiwan",

              "source-layer":
                  "transportation",

              "minzoom": 6,

              "layout": {

                "line-cap":
                    "round",

                "line-join":
                    "round"
              },

              "paint": {

                "line-color":
                    "#B9B9B9",

                "line-width": [

                  "interpolate",

                  ["linear"],

                  ["zoom"],

                  6, 1.0,

                  10, 2.0,

                  13, 4.0,

                  15, 8.0
                ]
              }
            },


            {
              "id": "road",

              "type": "line",

              "source": "taiwan",

              "source-layer":
                  "transportation",

              "minzoom": 6,

              "layout": {

                "line-cap":
                    "round",

                "line-join":
                    "round"
              },

              "paint": {

                "line-color":
                    "#FFFFFF",

                "line-width": [

                  "interpolate",

                  ["linear"],

                  ["zoom"],

                  6, 0.5,

                  10, 1.3,

                  13, 3.0,

                  15, 6.0
                ]
              }
            },


            {
              "id": "building",

              "type": "fill",

              "source": "taiwan",

              "source-layer":
                  "building",

              "minzoom": 13,

              "paint": {

                "fill-color":
                    "#D7D1C8",

                "fill-outline-color":
                    "#C0BAB1"
              }
            }


          ]
        }
        """.trimIndent()
    }
}