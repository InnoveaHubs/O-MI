// Generated by CoffeeScript 1.9.3
(function() {
  var constsExt, utilExt,
    slice = [].slice;

  utilExt = function($, parent) {
    var my, validators;
    my = parent.util = {};
    my.urlmatch = /^(0$|(https?|wss?):\/\/((((([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(%[\da-f]{2})|[!\$&'\(\)\*\+,;=]|:)*@)?(((\d|[1-9]\d|1\d\d|2[0-4]\d|25[0-5])\.(\d|[1-9]\d|1\d\d|2[0-4]\d|25[0-5])\.(\d|[1-9]\d|1\d\d|2[0-4]\d|25[0-5])\.(\d|[1-9]\d|1\d\d|2[0-4]\d|25[0-5]))|((([a-z]|\d|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(([a-z]|\d|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])*([a-z]|\d|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])))\.)+(([a-z]|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(([a-z]|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])*([a-z]|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])))\.?)|(localhost))(:\d*)?)(\/((([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(%[\da-f]{2})|[!\$&'\(\)\*\+,;=]|:|@)+(\/(([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(%[\da-f]{2})|[!\$&'\(\)\*\+,;=]|:|@)*)*)?)?(\?((([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(%[\da-f]{2})|[!\$&'\(\)\*\+,;=]|:|@)|[\uE000-\uF8FF]|\/|\?)*)?(\#((([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(%[\da-f]{2})|[!\$&'\(\)\*\+,;=]|:|@)|\/|\?)*)?(?:<|"|\s|$))/i;
    my.validators = validators = {};
    validators.nonEmpty = function(s) {
      if (s !== "") {
        return s;
      } else {
        return null;
      }
    };
    validators.number = function(s) {
      var a;
      if (s == null) {
        return s;
      }
      a = s.replace(',', '.');
      if ($.isNumeric(a)) {
        return parseFloat(a);
      } else {
        return null;
      }
    };
    validators.integer = function(x) {
      if ((x != null) && x % 1 === 0) {
        return x;
      } else {
        return null;
      }
    };
    validators.greaterThan = function(y) {
      return function(x) {
        if ((x != null) && x > y) {
          return x;
        } else {
          return null;
        }
      };
    };
    validators.greaterThanEq = function(y) {
      return function(x) {
        if ((x != null) && x >= y) {
          return x;
        } else {
          return null;
        }
      };
    };
    validators.equals = function(y) {
      return function(x) {
        if ((x != null) && x === y) {
          return x;
        } else {
          return null;
        }
      };
    };
    validators.or = function() {
      var vs;
      vs = 1 <= arguments.length ? slice.call(arguments, 0) : [];
      return function(c) {
        var i, len, res, v;
        if (vs.length === 0) {
          return null;
        }
        for (i = 0, len = vs.length; i < len; i++) {
          v = vs[i];
          res = v(c);
          if (res != null) {
            return res;
          }
        }
        return null;
      };
    };
    validators.url = function(s) {
      if (my.urlmatch.test(s)) {
        return s;
      } else {
        return null;
      }
    };
    my.cloneAbove = function(target, callback) {
      return my.cloneElem(target.prev(), callback);
    };
    my.cloneElem = function(target, callback) {
      var cloned;
      cloned = target.clone();
      cloned.find("input").val("");
      cloned.hide();
      target.after(cloned);
      return typeof callback === "function" ? callback(cloned) : void 0;
    };
    my.flash = function(jqueryElem) {
      jqueryElem.addClass('flash');
      return window.setTimeout((function() {
        return jqueryElem.removeClass('flash');
      }), 1000);
    };
    my.animatedShowRow = function(jqueryElem, insertFunction, callback) {
      if (insertFunction == null) {
        insertFunction = null;
      }
      if (callback == null) {
        callback = null;
      }
      if (jqueryElem.length === 0) {
        return;
      }
      if (typeof insertFunction === "function") {
        insertFunction();
      }
      if (typeof callback === "function") {
        callback();
      }
    };
    return parent;
  };

  constsExt = function($, parent, util) {
    var URLHighlightOverlay, afterWaits, my, openOdfContextmenu;
    my = parent.consts = {};
    my.codeMirrorSettings = {
      mode: "text/xml",
      lineNumbers: true,
      lineWrapping: true,
      viewportMargin: 150
    };
    my.icon = {
      objects: "glyphicon glyphicon-tree-deciduous",
      object: "glyphicon glyphicon-folder-open",
      method: "glyphicon glyphicon-flash",
      infoitem: "glyphicon glyphicon-apple",
      metadata: "glyphicon glyphicon-info-sign",
      description: "glyphicon glyphicon-info-sign"
    };
    my.addOdfTreeNode = function(parent, path, name, treeTypeName, callback) {
      var tree;
      if (callback == null) {
        callback = null;
      }
      tree = WebOmi.consts.odfTree;
      return tree.create_node(parent, {
        id: path,
        text: name,
        type: treeTypeName
      }, "first", function(node) {
        tree.open_node(parent, null, 500);
        return typeof callback === "function" ? callback(node) : void 0;
      });
    };
    openOdfContextmenu = function(target) {
      var createNode;
      createNode = function(particle, odfName, treeTypeName, defaultId) {
        return {
          label: "Add " + particle + " " + odfName,
          icon: my.icon[treeTypeName],
          _disabled: my.odfTree.settings.types[target.type].valid_children.indexOf(treeTypeName) === -1,
          action: function(data) {
            var idName, name, path, tree;
            tree = WebOmi.consts.odfTree;
            parent = tree.get_node(data.reference);
            name = defaultId != null ? window.prompt("Enter a name for the new " + odfName + ":", defaultId) : odfName;
            idName = idesc(name);
            path = parent.id + "/" + idName;
            if ($(jqesc(path)).length > 0) {
              tree.select_node(path);
            } else {
              return my.addOdfTreeNode(parent, path, name, treeTypeName);
            }
          }
        };
      };
      return {
        helptxt: {
          label: "For write request:",
          icon: "glyphicon glyphicon-pencil",
          action: function() {
            return my.ui.request.set("write", false);
          },
          separator_after: true
        },
        add_info: $.extend(createNode("an", "InfoItem", "infoitem", "MyInfoItem"), {
          action: function(data) {
            var tree;
            tree = WebOmi.consts.odfTree;
            parent = tree.get_node(data.reference);
            $('#infoItemParent').val(parent.id);
            return WebOmi.consts.infoItemDialog.modal("show");
          }
        }),
        add_obj: createNode("an", "Object", "object", "MyObject"),
        add_metadata: createNode("a", "MetaData", "metadata", null),
        add_decsription: createNode("a", "description", "description", null)
      };
    };
    my.odfTreeSettings = {
      plugins: ["checkbox", "types", "contextmenu", "sort"],
      core: {
        error: function(msg) {
          return WebOmi.debug(msg);
        },
        force_text: true,
        check_callback: true,
        data: function(node, callback) {
          var parents, path, serverUrl, that, tree, xhr;
          if (node.id === '#') {
            callback.call(this, [
              {
                id: "Objects",
                text: "Objects",
                state: {
                  opened: false
                },
                type: "objects",
                parent: "#",
                children: true
              }
            ]);
            return;
          }
          that = this;
          tree = WebOmi.consts.odfTreeDom.jstree();
          node = tree.get_node(node, true);
          parents = $.makeArray(node.parentsUntil(WebOmi.consts.odfTreeDom, "li"));
          parents.reverse();
          parents.push(node);
          parents = (function() {
            var i, len, results;
            results = [];
            for (i = 0, len = parents.length; i < len; i++) {
              node = parents[i];
              results.push(encodeURIComponent(tree.get_node(node).text));
            }
            return results;
          })();
          path = parents.join('/');
          serverUrl = my.serverUrl.val();
          serverUrl = serverUrl.replace(/^wss:/, "https:").replace(/^ws:/, "http:");
          xhr = $.get({
            url: serverUrl + path,
            dataType: "xml",
            cache: false,
            success: (function(parentPath) {
              return function(xml) {
                var child, children, data;
                data = WebOmi.formLogic.OdfToJstree(xml.documentElement, path);
                children = (function() {
                  var i, len, ref, results;
                  ref = data.children;
                  results = [];
                  for (i = 0, len = ref.length; i < len; i++) {
                    child = ref[i];
                    switch (child.type) {
                      case "objects":
                      case "object":
                        child.children = true;
                    }
                    results.push(child);
                  }
                  return results;
                })();
                return callback.call(that, data.children);
              };
            })(path)
          });
          return xhr.fail(function(xhr, msg, err) {
            WebOmi.debug(["O-DF GET fail", xhr, msg, err]);
            return alert("Failed to get Object(s): " + msg);
          });
        }
      },
      types: {
        "default": {
          icon: "odf-objects " + my.icon.objects,
          valid_children: ["object"]
        },
        object: {
          icon: "odf-object " + my.icon.object,
          valid_children: ["object", "infoitem", "description"]
        },
        objects: {
          icon: "odf-objects " + my.icon.objects,
          valid_children: ["object"]
        },
        infoitem: {
          icon: "odf-infoitem " + my.icon.infoitem,
          valid_children: ["metadata", "description"]
        },
        method: {
          icon: "odf-method " + my.icon.method,
          valid_children: ["metadata", "description"]
        },
        metadata: {
          icon: "odf-metadata " + my.icon.metadata,
          valid_children: []
        },
        description: {
          icon: "odf-description " + my.icon.description,
          valid_children: []
        }
      },
      checkbox: {
        three_state: false,
        keep_selected_style: true,
        cascade: "up+undetermined",
        tie_selection: true
      },
      contextmenu: {
        show_at_node: true,
        items: openOdfContextmenu
      }
    };
    afterWaits = [];
    my.afterJquery = function(fn) {
      return afterWaits.push(fn);
    };
    URLHighlightOverlay = {
      token: function(stream, state) {
        if (stream.match(util.urlmatch)) {
          stream.backUp(1);
          return "link";
        }
        while ((stream.next() != null) && !stream.match(util.urlmatch, false)) {
          null;
        }
        return null;
      }
    };
    $(function() {
      var basicInput, fn, i, language, len, loc, proto, requestTip, results, v;
      my.responseCMSettings = $.extend({
        readOnly: true
      }, my.codeMirrorSettings);
      my.requestCodeMirror = CodeMirror.fromTextArea($("#requestArea")[0], my.codeMirrorSettings);
      my.responseCodeMirror = CodeMirror.fromTextArea($("#responseArea")[0], my.responseCMSettings);
      my.responseDiv = $('.response .CodeMirror');
      my.responseDiv.hide();
      my.responseCodeMirror.addOverlay(URLHighlightOverlay);
      $('.well.response').delegate(".cm-link", "click", function(event) {
        var url;
        url = $(event.target).text();
        return window.open(url, '_blank');
      });
      my.serverUrl = $('#targetService');
      my.odfTreeDom = $('#nodetree');
      my.requestSelDom = $('.requesttree');
      my.readAllBtn = $('#readall');
      my.sendBtn = $('#send');
      my.resetAllBtn = $('#resetall');
      my.progressBar = $('.response .progress-bar');
      my.sortOdfTreeCheckbox = $('#sortOdfTree');
      loc = window.location;
      proto = loc.protocol === "https:" ? "wss:" : "ws:";
      my.serverUrl.val(proto + "//" + loc.host + loc.pathname.substr(0, loc.pathname.indexOf("html/")));
      my.odfTreeDom.jstree(my.odfTreeSettings);
      my.odfTree = my.odfTreeDom.jstree();
      my.odfTree.set_type('Objects', 'objects');
      my.requestSelDom.jstree({
        core: {
          themes: {
            icons: false
          },
          multiple: false
        }
      });
      my.requestSel = my.requestSelDom.jstree();
      $('[data-toggle="tooltip"]').tooltip({
        container: 'body'
      });
      requestTip = function(selector, text) {
        return my.requestSelDom.find(selector).children("a").tooltip({
          title: text,
          placement: "right",
          container: "body",
          trigger: "hover"
        });
      };
      requestTip("#readReq", "Requests that can be used to get data from server. Use one of the below cases.");
      requestTip("#read", "Single request for latest or old data with various parameters.");
      requestTip("#subscription", "Create a subscription for data with given interval. Returns requestID which can be used to poll or cancel");
      requestTip("#poll", "Request and empty buffered data for callbackless subscription.");
      requestTip("#cancel", "Cancel and remove an active subscription.");
      requestTip("#write", "Write new data to the server. NOTE: Right click the above odf tree to create new elements.");
      basicInput = function(selector, validator) {
        if (validator == null) {
          validator = util.validators.nonEmpty;
        }
        return {
          ref: $(selector),
          get: function() {
            return this.ref.val();
          },
          set: function(val) {
            return this.ref.val(val);
          },
          validate: function() {
            var val, validatedVal, validationContainer;
            val = this.get();
            validationContainer = this.ref.closest(".form-group");
            validatedVal = validator(val);
            if ((validatedVal != null) || this.ref.prop("disabled")) {
              validationContainer.removeClass("has-error").addClass("has-success");
            } else {
              validationContainer.removeClass("has-success").addClass("has-error");
            }
            return validatedVal;
          },
          bindTo: function(callback) {
            return this.ref.on("input", (function(_this) {
              return function() {
                return callback(_this.validate());
              };
            })(this));
          }
        };
      };
      v = util.validators;
      my.ui = {
        request: {
          ref: my.requestSelDom,
          set: function(reqName, preventEvent) {
            var tree;
            if (preventEvent == null) {
              preventEvent = true;
            }
            tree = this.ref.jstree();
            if (!tree.is_selected(reqName)) {
              tree.deselect_all();
              return tree.select_node(reqName, preventEvent, false);
            }
          },
          get: function() {
            return this.ref.jstree().get_selected[0];
          }
        },
        ttl: basicInput('#ttl', function(a) {
          return (v.or(v.greaterThanEq(0), v.equals(-1)))(v.number(v.nonEmpty(a)));
        }),
        callback: basicInput('#callback', v.url),
        requestID: basicInput('#requestID', function(a) {
          return v.integer(v.number(v.nonEmpty(a)));
        }),
        odf: {
          ref: my.odfTreeDom,
          get: function() {
            return my.odfTree.get_selected();
          },
          set: function(vals, preventEvent) {
            var i, len, node, results;
            if (preventEvent == null) {
              preventEvent = true;
            }
            my.odfTree.deselect_all(true);
            if ((vals != null) && vals.length > 0) {
              results = [];
              for (i = 0, len = vals.length; i < len; i++) {
                node = vals[i];
                results.push(my.odfTree.select_node(node, preventEvent, false));
              }
              return results;
            }
          }
        },
        interval: basicInput('#interval', function(a) {
          return (v.or(v.greaterThanEq(0), v.equals(-1), v.equals(-2)))(v.number(v.nonEmpty(a)));
        }),
        newest: basicInput('#newest', function(a) {
          return (v.greaterThan(0))(v.integer(v.number(v.nonEmpty(a))));
        }),
        oldest: basicInput('#oldest', function(a) {
          return (v.greaterThan(0))(v.integer(v.number(v.nonEmpty(a))));
        }),
        begin: $.extend(basicInput('#begin'), {
          set: function(val) {
            return this.ref.data("DateTimePicker").date(val);
          },
          get: function() {
            var mementoTime;
            mementoTime = this.ref.data("DateTimePicker").date();
            if (mementoTime != null) {
              return mementoTime.toISOString();
            } else {
              return null;
            }
          },
          bindTo: function(callback) {
            return this.ref.on("dp.change", (function(_this) {
              return function() {
                return callback(_this.validate());
              };
            })(this));
          }
        }),
        end: $.extend(basicInput('#end'), {
          set: function(val) {
            return this.ref.data("DateTimePicker").date(val);
          },
          get: function() {
            var mementoTime;
            mementoTime = this.ref.data("DateTimePicker").date();
            if (mementoTime != null) {
              return mementoTime.toISOString();
            } else {
              return null;
            }
          },
          bindTo: function(callback) {
            return this.ref.on("dp.change", (function(_this) {
              return function() {
                return callback(_this.validate());
              };
            })(this));
          }
        }),
        requestDoc: {
          ref: my.requestCodeMirror,
          get: function() {
            return WebOmi.formLogic.getRequest();
          },
          set: function(val) {
            return WebOmi.formLogic.setRequest(val);
          }
        }
      };
      language = window.navigator.userLanguage || window.navigator.language;
      if (!moment.localeData(language)) {
        language = "en";
      }
      my.ui.end.ref.datetimepicker({
        locale: language
      });
      my.ui.begin.ref.datetimepicker({
        locale: language
      });
      my.ui.begin.ref.on("dp.change", function(e) {
        return my.ui.end.ref.data("DateTimePicker").minDate(e.date);
      });
      my.ui.end.ref.on("dp.change", function(e) {
        return my.ui.begin.ref.data("DateTimePicker").maxDate(e.date);
      });
      my.ui.begin.ref.closest("a.tooltip").on('click', function() {
        return my.ui.begin.ref.data("DateTimePicker").toggle();
      });
      my.ui.end.ref.closest("a.tooltip").on('click', function() {
        return my.ui.end.ref.data("DateTimePicker").toggle();
      });
      my.afterJquery = function(fn) {
        return fn();
      };
      results = [];
      for (i = 0, len = afterWaits.length; i < len; i++) {
        fn = afterWaits[i];
        results.push(fn());
      }
      return results;
    });
    return parent;
  };

  window.WebOmi = utilExt($, window.WebOmi || {});

  window.WebOmi = constsExt($, window.WebOmi, window.WebOmi.util);

  window.WebOmi.error = function() {
    var msgs;
    msgs = 1 <= arguments.length ? slice.call(arguments, 0) : [];
    return alert(msgs.join(", "));
  };

  window.WebOmi.debug = function() {
    var msgs;
    msgs = 1 <= arguments.length ? slice.call(arguments, 0) : [];
    return console.log.apply(console, msgs);
  };

  window.jqesc = function(mySel) {
    return '#' + mySel.replace(/(\[|\]|!|"|#|\$|%|&|\'|\(|\)|\*|\+|\,|\.|\/|\:|\;|\?|@)/g, "\\$1").replace(/( )/g, "_");
  };

  window.idesc = function(myId) {
    return myId.replace(/( )/g, "_");
  };

  String.prototype.trim = String.prototype.trim || function() {
    return String(this).replace(/^\s+|\s+$/g, '');
  };

  window.Initialize = "ready";

}).call(this);
