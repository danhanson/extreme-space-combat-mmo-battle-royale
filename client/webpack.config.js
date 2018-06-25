const webpack = require('webpack')
const path = require('path')
const MiniCssExtractPlugin = require('mini-css-extract-plugin')
const HtmlWebpackPlugin = require('html-webpack-plugin')
// const BabelMinifyPlugin = require('babel-minify-webpack-plugin')

module.exports = (env, { mode = 'development' }) => {
  const plugins = [
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
      'precess.env.NODE_ENV': mode
    }),
    new MiniCssExtractPlugin({
      filename: '[name].css',
      chunkFilename: '[id].css'
    })
  ]
  if (mode === 'production') {
    plugins.push(new MiniCssExtractPlugin())
  }
  return {
    entry: ['babel-polyfill', './src/index.js'],
    output: {
      path: path.resolve(__dirname, 'dist'),
      filename: '[name].js'
    },
    module: {
      rules: [
        {
          test: /\.js$/,
          include: [path.resolve(__dirname, 'src')],
          loader: 'babel-loader'
        },
        {
          test: /\.styl$/,
          use: [
            {
              loader: MiniCssExtractPlugin.loader
            },
            {
              loader: 'css-loader'
            },
            {
              loader: 'stylus-loader'
            }
          ]
        }
      ]
    },
    devtool: 'source-map',
    mode,
    plugins,
    optimization: {
      // minimizer: [ new BabelMinifyPlugin() ] breaks source maps
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
}
