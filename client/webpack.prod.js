const webpack = require('webpack')
const path = require('path')
const MiniCssExtractPlugin = require('mini-css-extract-plugin')
const HtmlWebpackPlugin = require('html-webpack-plugin')

module.exports = {
  output: {
    path: path.resolve(__dirname, 'dist'),
    filename: '[name].js'
  },
  module: {
    rules: [
      {
        test: /\.js$/,
        include: [path.resolve(__dirname, 'src')],
        loader: 'babel-loader',
        options: {
          presets: [
            'env',
            {
              modules: false
            }
          ],
          plugins: ['syntax-dynamic-import']
        }
      },
      {
        test: /\.styl$/,
        use: [
          {
            loader: MiniCssExtractPlugin.loader
          },
          {
            loader: 'css-loader',
            options: {
              sourceMap: true,
              importLoaders: 1
            }
          },
          {
            loader: 'stylus-loader'
          }
        ]
      }
    ]
  },
  devtool: 'source-map',
  mode: 'production',
  plugins: [
    new HtmlWebpackPlugin({
      title: 'Space Game 2',
      filename: 'index.html',
      meta: {
        charset: 'UTF-8',
        viewport: 'width=device-width, initial-scale=1'
      },
      minify: true
    }),
    new webpack.DefinePlugin({
      'precess.env.NODE_ENV': require('production')
    })
  ],
  optimization: {
    splitChunks: {
      chunks: 'async',
      minSize: 30000,
      minChunks: 1,
      name: false,
      cacheGroups: {
        vendors: {
          test: /[\\/]node_modules[\\/]/,
          priority: -10
        }
      }
    }
  }
}
